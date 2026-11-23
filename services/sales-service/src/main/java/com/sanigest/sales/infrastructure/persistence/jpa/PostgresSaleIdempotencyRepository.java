package com.sanigest.sales.infrastructure.persistence.jpa;

import com.sanigest.sales.application.dto.CreatedSaleResponse;
import com.sanigest.sales.application.exception.IdempotencyConflict;
import com.sanigest.sales.application.exception.IdempotencyInProgress;
import com.sanigest.sales.application.port.out.persistence.SaleIdempotencyRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
class PostgresSaleIdempotencyRepository implements SaleIdempotencyRepository {
  private static final String SCOPE =
      """
      company_id = :company AND actor_id = :actor AND operation = :operation
      AND idempotency_key = :key
      """;
  private static final String OWNED = SCOPE + " AND claim_token = :token";
  private static final int RESPONSE_VERSION = 1;

  private final NamedParameterJdbcTemplate jdbc;
  private final EntityManager entityManager;
  private final TransactionTemplate transaction;
  private final MeterRegistry metrics;
  private final long retentionMillis;
  private final long leaseMillis;

  PostgresSaleIdempotencyRepository(
      NamedParameterJdbcTemplate jdbc,
      EntityManager entityManager,
      PlatformTransactionManager manager,
      MeterRegistry metrics,
      @Value("${sanigest.sales.idempotency.retention}") Duration retention,
      @Value("${sanigest.sales.idempotency.lease}") Duration lease) {
    this.jdbc = jdbc;
    this.entityManager = entityManager;
    this.metrics = metrics;
    this.retentionMillis = positiveMillis(retention);
    this.leaseMillis = positiveMillis(lease);
    this.transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    transaction.setTimeout(10);
  }

  @Override
  public Resolution resolve(Request request) {
    var timer = Timer.start(metrics);
    try {
      return transaction.execute(status -> claimOrReplay(request));
    } catch (IdempotencyConflict conflict) {
      event("conflict");
      throw conflict;
    } catch (IdempotencyInProgress busy) {
      event("in_progress");
      throw busy;
    } catch (DataAccessException failure) {
      if (failure.getMostSpecificCause() instanceof SQLException sql
          && "55P03".equals(sql.getSQLState())) {
        event("in_progress");
        throw new IdempotencyInProgress();
      }
      event("persistence_failure");
      throw failure;
    } finally {
      timer.stop(metrics.timer("sales.idempotency.resolve"));
    }
  }

  private Resolution claimOrReplay(Request request) {
    // Read completed responses and active claims without contending for their row locks.
    var parameters = scope(request);
    var existing = lookup(request, parameters);
    if (existing != null) {
      return existing;
    }
    jdbc.getJdbcOperations().execute("SET LOCAL lock_timeout = '1s'");
    UUID token = UUID.randomUUID();
    int claimed =
        jdbc.update(
            """
            INSERT INTO sales.sale_idempotency_keys AS pending
                (company_id, actor_id, operation, idempotency_key, fingerprint_version, request_hash,
                 claim_token, created_at, expires_at)
            VALUES (:company, :actor, :operation, :key, :version, :hash, :token,
                    statement_timestamp(), statement_timestamp() + :lease * interval '1 millisecond')
            ON CONFLICT (company_id, actor_id, operation, idempotency_key) DO UPDATE
            SET claim_token = EXCLUDED.claim_token,
                expires_at = clock_timestamp() + :lease * interval '1 millisecond'
            WHERE pending.claim_token IS NOT NULL AND pending.expires_at <= clock_timestamp()
              AND pending.request_hash = EXCLUDED.request_hash
              AND pending.fingerprint_version = EXCLUDED.fingerprint_version
            """,
            parameters.addValue("token", token).addValue("lease", leaseMillis));
    if (claimed == 1) {
      return new Claim(token);
    }
    // ON CONFLICT can observe a row committed after the initial lookup.
    var concurrent = lookup(request, parameters);
    if (concurrent != null) {
      return concurrent;
    }
    throw new IdempotencyInProgress();
  }

  private Resolution lookup(Request request, MapSqlParameterSource parameters) {
    return jdbc.query(
        """
        SELECT request_hash, fingerprint_version, sale_id, response_status, response_body,
               expires_at > clock_timestamp() AS active
        FROM sales.sale_idempotency_keys WHERE
        """
            + SCOPE,
        parameters,
        rows -> {
          if (!rows.next()) {
            return null;
          }
          if (request.version() != rows.getInt("fingerprint_version")
              || !request.hash().equals(rows.getString("request_hash"))) {
            throw new IdempotencyConflict();
          }
          UUID saleId = rows.getObject("sale_id", UUID.class);
          if (saleId != null) {
            event("replay");
            return new Replay(
                new CreatedSaleResponse(
                    saleId, rows.getInt("response_status"), rows.getString("response_body")));
          }
          if (rows.getBoolean("active")) {
            throw new IdempotencyInProgress();
          }
          return null;
        });
  }

  @Override
  public CreatedSaleResponse complete(
      Request request, Claim claim, Supplier<CreatedSaleResponse> creation) {
    try {
      var result = transaction.execute(status -> save(request, claim, creation));
      event("created");
      return result;
    } catch (IdempotencyInProgress busy) {
      event("in_progress");
      throw busy;
    } catch (RuntimeException failure) {
      event("persistence_failure");
      throw failure;
    }
  }

  private CreatedSaleResponse save(
      Request request, Claim claim, Supplier<CreatedSaleResponse> creation) {
    var parameters = scope(request).addValue("token", claim.token());
    var valid =
        jdbc.query(
            """
            SELECT expires_at > clock_timestamp() FROM sales.sale_idempotency_keys WHERE
            """
                + OWNED
                + " FOR UPDATE",
            parameters,
            rows -> rows.next() && rows.getBoolean(1));
    if (!Boolean.TRUE.equals(valid)) {
      throw new IdempotencyInProgress();
    }
    var result = creation.get();
    entityManager.flush();
    int updated =
        jdbc.update(
            """
            UPDATE sales.sale_idempotency_keys
            SET claim_token = NULL, sale_id = :saleId, response_body = :body, response_status = :status,
                response_version = :responseVersion, completed_at = statement_timestamp(),
                expires_at = statement_timestamp() + :retention * interval '1 millisecond'
            WHERE
            """
                + OWNED,
            parameters
                .addValue("saleId", result.saleId())
                .addValue("body", result.body())
                .addValue("status", result.status())
                .addValue("responseVersion", RESPONSE_VERSION)
                .addValue("retention", retentionMillis));
    if (updated != 1) {
      throw new IdempotencyInProgress();
    }
    return result;
  }

  @Override
  public void release(Request request, Claim claim) {
    transaction.executeWithoutResult(status ->
        jdbc.update(
            "DELETE FROM sales.sale_idempotency_keys WHERE " + OWNED,
            scope(request).addValue("token", claim.token())));
  }

  private static MapSqlParameterSource scope(Request request) {
    return new MapSqlParameterSource("company", request.companyId())
        .addValue("actor", request.actorId())
        .addValue("operation", request.operation())
        .addValue("key", request.key())
        .addValue("version", request.version())
        .addValue("hash", request.hash());
  }

  private static long positiveMillis(Duration duration) {
    long millis = duration.toMillis();
    if (millis <= 0) {
      throw new IllegalArgumentException(
          "Sales idempotency durations must be positive milliseconds");
    }
    return millis;
  }

  private void event(String outcome) {
    metrics.counter("sales.idempotency.requests", "outcome", outcome).increment();
  }
}
