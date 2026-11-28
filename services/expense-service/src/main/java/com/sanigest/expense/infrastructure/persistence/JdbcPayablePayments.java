package com.sanigest.expense.infrastructure.persistence;

import com.sanigest.events.outbox.PaymentOutbox;
import com.sanigest.events.payment.FinancialWriteFingerprint;
import com.sanigest.events.payment.PaymentEventTypeV1;
import com.sanigest.events.payment.PaymentEventV1;
import com.sanigest.expense.application.ExpenseFailure;
import com.sanigest.expense.application.ExpenseFailure.Kind;
import com.sanigest.expense.application.port.out.integration.PaymentAccounts;
import com.sanigest.expense.application.port.out.persistence.PayablePayments;
import com.sanigest.expense.application.usecase.RegisterPayablePayment;
import com.sanigest.expense.domain.model.PayablePayment.Details;
import com.sanigest.expense.domain.model.PayablePayment.Result;
import com.sanigest.expense.domain.model.PayablePayment.Status;
import com.sanigest.security.RequestCorrelation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
class JdbcPayablePayments implements PayablePayments {
  private final JdbcClient jdbc;
  private final ObjectMapper json;
  private final PaymentAccounts accounts;
  private final PaymentOutbox outbox;

  JdbcPayablePayments(
      JdbcClient jdbc, ObjectMapper json, PaymentAccounts accounts, PaymentOutbox outbox) {
    this.jdbc = jdbc;
    this.json = json;
    this.accounts = accounts;
    this.outbox = outbox;
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Result register(UUID company, UUID actor, UUID payable, String key, Details payment) {
    var balance =
        jdbc.sql(
                """
                SELECT original_amount - paid_amount AS outstanding, cancelled_at IS NOT NULL AS cancelled
                FROM expense.payables WHERE company_id = :company AND expense_id = :payable FOR UPDATE
                """)
            .param("company", company)
            .param("payable", payable)
            .query((row, index) ->
                new Balance(row.getBigDecimal("outstanding"), row.getBoolean("cancelled")))
            .optional()
            .orElseThrow(() -> new ExpenseFailure(Kind.NOT_FOUND, "PAYABLE_NOT_FOUND"));

    UUID id = UUID.randomUUID();
    String request = json.writeValueAsString(payment);
    String fingerprint = FinancialWriteFingerprint.v1("payable-payment", payable, payment);
    int inserted =
        jdbc.sql(
                """
                INSERT INTO expense.payable_payments
                  (id, company_id, payable_id, actor_id, request_key, request_body, request_fingerprint)
                VALUES (:id, :company, :payable, :actor, :key, CAST(:request AS jsonb), :fingerprint)
                ON CONFLICT (company_id, actor_id, request_key) DO NOTHING
                """)
            .param("id", id)
            .param("company", company)
            .param("payable", payable)
            .param("actor", actor)
            .param("key", key)
            .param("request", request)
            .param("fingerprint", fingerprint)
            .update();
    if (inserted == 0) {
      return replay(company, actor, payable, key, request, fingerprint);
    }

    if (balance.cancelled()) {
      throw new ExpenseFailure(Kind.CONFLICT, "PAYABLE_CANCELLED");
    }
    BigDecimal outstanding = balance.outstanding().subtract(payment.amount());
    if (outstanding.signum() < 0) {
      throw new ExpenseFailure(Kind.CONFLICT, "PAYABLE_PAYMENT_EXCEEDS_BALANCE");
    }
    accounts.requireEligible(company, payment.accountId(), payment.method());

    jdbc.sql(
            """
            UPDATE expense.payables SET paid_amount = paid_amount + :amount
            WHERE company_id = :company AND expense_id = :payable
            """)
        .param("amount", payment.amount())
        .param("company", company)
        .param("payable", payable)
        .update();

    var registeredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    var businessTime = registeredAt.atZone(RegisterPayablePayment.BUSINESS_ZONE);
    var occurredAt =
        payment.date().equals(businessTime.toLocalDate())
            ? registeredAt
            : payment.date().atStartOfDay(RegisterPayablePayment.BUSINESS_ZONE).toInstant();
    String correlation = RequestCorrelation.current();
    outbox.enqueue(
        company,
        new PaymentEventV1(
            UUID.randomUUID(),
            PaymentEventTypeV1.PayablePaymentRegisteredV1,
            PaymentEventV1.VERSION,
            occurredAt,
            company,
            id,
            correlation,
            id,
            null,
            payment.accountId(),
            payment.method(),
            payment.amount(),
            "DOP",
            null,
            null,
            payable,
            actor));

    var result =
        new Result(
            id,
            payable,
            actor,
            payment.amount(),
            payment.method(),
            payment.accountId(),
            payment.date(),
            payment.reference(),
            payment.notes(),
            registeredAt,
            outstanding,
            outstanding.signum() == 0 ? Status.PAID : Status.PARTIALLY_PAID);
    complete(company, result, correlation);
    return result;
  }

  private void complete(UUID company, Result result, String correlation) {
    jdbc.sql(
            "UPDATE expense.payable_payments SET response_body = CAST(:response AS jsonb) WHERE id"
                + " = :id")
        .param("response", json.writeValueAsString(result))
        .param("id", result.paymentId())
        .update();
    jdbc.sql(
            """
            INSERT INTO expense.audit_log
                (id, company_id, actor_id, operation, resource_id, state, correlation_id)
            VALUES (:id, :company, :actor, 'PAYABLE_PAYMENT_REGISTERED', :payment, :state, :trace)
            """)
        .param("id", UUID.randomUUID())
        .param("company", company)
        .param("actor", result.actorId())
        .param("payment", result.paymentId())
        .param("state", result.status().name())
        .param("trace", correlation)
        .update();
  }

  private Result replay(
      UUID company, UUID actor, UUID payable, String key, String request, String fingerprint) {
    return jdbc.sql(
            """
            SELECT payable_id = :payable
                   AND COALESCE(request_fingerprint = :fingerprint,
                                request_body = CAST(:request AS jsonb)) AS matches,
                   response_body
            FROM expense.payable_payments
            WHERE company_id = :company AND actor_id = :actor AND request_key = :key
            """)
        .param("payable", payable)
        .param("request", request)
        .param("fingerprint", fingerprint)
        .param("company", company)
        .param("actor", actor)
        .param("key", key)
        .query((row, index) -> {
          if (!row.getBoolean("matches")) {
            throw new ExpenseFailure(Kind.CONFLICT, "PAYABLE_PAYMENT_IDEMPOTENCY_CONFLICT");
          }
          return json.readValue(row.getString("response_body"), Result.class);
        })
        .single();
  }

  private record Balance(BigDecimal outstanding, boolean cancelled) {}
}
