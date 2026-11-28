package com.sanigest.finance.infrastructure.persistence;

import com.sanigest.finance.application.dto.ManualMovement.Document;
import com.sanigest.finance.application.dto.ManualMovement.Request;
import com.sanigest.finance.application.dto.ManualMovement.Type;
import com.sanigest.finance.application.exception.ManualMovementConflict;
import com.sanigest.finance.application.port.out.persistence.ManualMovementStore;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
class PostgresManualCashMovements implements ManualMovementStore {
  private final JdbcClient jdbc;
  private final ObjectMapper json;

  PostgresManualCashMovements(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  @Transactional
  public UUID save(
      UUID company,
      UUID actor,
      String key,
      String fingerprint,
      Request request,
      Document document) {
    UUID operation = UUID.randomUUID();
    int inserted =
        jdbc.sql(
                """
                INSERT INTO finance.manual_cash_operations
                  (company_id, actor_id, idempotency_key, fingerprint, id, details, document_type, document_content)
                VALUES (:company, :actor, :key, :fingerprint, :id, CAST(:details AS jsonb), :type, :content)
                ON CONFLICT (company_id, actor_id, idempotency_key) DO NOTHING
                """)
            .param("company", company)
            .param("actor", actor)
            .param("key", key)
            .param("fingerprint", fingerprint)
            .param("id", operation)
            .param("details", json.writeValueAsString(request))
            .param("type", document == null ? null : document.contentType(), java.sql.Types.VARCHAR)
            .param("content", document == null ? null : document.content(), java.sql.Types.BINARY)
            .update();
    if (inserted == 0) {
      return jdbc.sql(
              """
              SELECT id FROM finance.manual_cash_operations
              WHERE company_id = :company AND actor_id = :actor AND idempotency_key = :key
                AND fingerprint = :fingerprint
              """)
          .param("company", company)
          .param("actor", actor)
          .param("key", key)
          .param("fingerprint", fingerprint)
          .query(UUID.class)
          .optional()
          .orElseThrow(ManualMovementConflict::new);
    }

    if (request.type() == Type.INTERNAL_TRANSFER) {
      // Use the same account order for opposite transfers when both accounts are new.
      UUID source = request.sourceAccountId();
      UUID destination = request.destinationAccountId();
      if (source.compareTo(destination) < 0) {
        ensureAccount(company, source);
        ensureAccount(company, destination);
      } else {
        ensureAccount(company, destination);
        ensureAccount(company, source);
      }
      insertLeg(company, actor, operation, request, source, "OUTFLOW");
    } else {
      ensureAccount(company, request.destinationAccountId());
    }
    insertLeg(
        company,
        actor,
        operation,
        request,
        request.destinationAccountId(),
        request.type() == Type.EXPENSE ? "OUTFLOW" : "INFLOW");
    return operation;
  }

  private void ensureAccount(UUID company, UUID account) {
    jdbc.sql(
            """
            INSERT INTO finance.cash_accounts (company_id, id) VALUES (:company, :account)
            ON CONFLICT DO NOTHING
            """)
        .param("company", company)
        .param("account", account)
        .update();
  }

  private void insertLeg(
      UUID company, UUID actor, UUID operation, Request request, UUID account, String direction) {
    UUID event = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO finance.cash_movements
              (event_id, company_id, event_type, source_id, payment_id, account_id, method, amount,
               currency, occurred_at, origin, direction, actor_id, trace_id, manual_operation_id)
            VALUES (:event, :company, :type, :event, :operation, :account, :method, :amount, 'DOP',
                    CAST(:date AS timestamp) AT TIME ZONE 'America/Santo_Domingo',
                    'MANUAL', :direction, :actor, :trace, :operation)
            """)
        .param("event", event)
        .param("company", company)
        .param("type", request.type().name())
        .param("operation", operation)
        .param("account", account)
        .param("method", request.method().name())
        .param("amount", request.amount())
        .param("date", request.date())
        .param("direction", direction)
        .param("actor", actor)
        .param("trace", MDC.get("traceId"), java.sql.Types.VARCHAR)
        .update();
  }

  @Override
  public Optional<Document> document(UUID company, UUID id) {
    return jdbc.sql(
            """
            SELECT document_type, document_content FROM finance.manual_cash_operations
            WHERE company_id = :company AND id = :id AND document_content IS NOT NULL
            """)
        .param("company", company)
        .param("id", id)
        .query((row, index) -> new Document(row.getString(1), row.getBytes(2)))
        .optional();
  }
}
