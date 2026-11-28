package com.sanigest.sales.infrastructure.persistence.jpa;

import com.sanigest.events.outbox.PaymentOutbox;
import com.sanigest.events.payment.FinancialWriteFingerprint;
import com.sanigest.events.payment.PaymentEventTypeV1;
import com.sanigest.events.payment.PaymentEventV1;
import com.sanigest.events.payment.PaymentMethodV1;
import com.sanigest.sales.application.exception.IdempotencyConflict;
import com.sanigest.sales.application.exception.InvoiceNotFound;
import com.sanigest.sales.application.exception.InvoicePaymentConflict;
import com.sanigest.sales.application.exception.PaymentAdmissionUnavailable;
import com.sanigest.sales.application.port.out.integration.PaymentAdmission;
import com.sanigest.sales.application.port.out.persistence.InvoicePayments;
import com.sanigest.sales.domain.enums.PaymentMethod;
import com.sanigest.sales.domain.enums.ReceivableStatus;
import com.sanigest.sales.domain.model.InvoicePayment;
import com.sanigest.sales.domain.model.Receivable;
import com.sanigest.security.RequestCorrelation;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
class PostgresInvoicePayments implements InvoicePayments {
  private final JdbcClient jdbc;
  private final SaleRows sales;
  private final PostgresReceivableRepository receivables;
  private final PaymentOutbox outbox;
  private final SalesAudit audit;
  private final ObjectMapper json;

  PostgresInvoicePayments(
      JdbcClient jdbc,
      SaleRows sales,
      PostgresReceivableRepository receivables,
      PaymentOutbox outbox,
      SalesAudit audit,
      ObjectMapper json) {
    this.jdbc = jdbc;
    this.sales = sales;
    this.receivables = receivables;
    this.outbox = outbox;
    this.audit = audit;
    this.json = json;
  }

  @Override
  public List<InvoicePayment.HistoryLine> findByShift(UUID company, UUID operator, UUID shift) {
    return jdbc.sql(
            """
            SELECT p.id AS payment_id, l.id AS line_id, p.invoice_id,
                   p.request_body ->> 'clientName' AS client_name, l.method, l.amount, p.balance
              FROM sales.invoice_payments p
              JOIN sales.invoice_payment_lines l ON l.payment_id = p.id
             WHERE p.company_id = :company AND p.operator_id = :operator AND p.shift_id = :shift
               AND p.registered_at IS NOT NULL
             ORDER BY p.registered_at DESC, p.id DESC, l.position
            """)
        .param("company", company)
        .param("operator", operator)
        .param("shift", shift)
        .query((row, index) ->
            new InvoicePayment.HistoryLine(
                row.getObject("payment_id", UUID.class),
                row.getObject("line_id", UUID.class),
                row.getObject("invoice_id", UUID.class),
                row.getString("client_name"),
                PaymentMethod.valueOf(row.getString("method")),
                row.getBigDecimal("amount"),
                row.getBigDecimal("balance")))
        .list();
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public InvoicePayment.Result register(
      UUID company,
      UUID operator,
      UUID invoiceId,
      String key,
      InvoicePayment.Request request,
      PaymentAdmission authority) {
    var saleId =
        jdbc.sql(
                """
                SELECT sale_id FROM sales.internal_invoices WHERE company_id = :company AND id = :invoice
                """)
            .param("company", company)
            .param("invoice", invoiceId)
            .query(UUID.class)
            .optional()
            .orElseThrow(InvoiceNotFound::new);

    sales.lockSale(company, saleId).orElseThrow(InvoiceNotFound::new);
    var payment = UUID.randomUUID();
    var requestBody = json.writeValueAsString(request);
    String fingerprint = FinancialWriteFingerprint.v1("invoice-payment", invoiceId, request);
    int inserted =
        jdbc.sql(
                """
                INSERT INTO sales.invoice_payments
                  (id, company_id, operator_id, invoice_id, idempotency_key, request_body, request_fingerprint)
                VALUES (:id, :company, :operator, :invoice, :key, CAST(:request AS jsonb), :fingerprint)
                ON CONFLICT (company_id, operator_id, idempotency_key) DO NOTHING
                """)
            .param("id", payment)
            .param("company", company)
            .param("operator", operator)
            .param("invoice", invoiceId)
            .param("key", key)
            .param("request", requestBody)
            .param("fingerprint", fingerprint)
            .update();
    if (inserted == 0) {
      return replay(company, operator, invoiceId, key, requestBody, fingerprint);
    }

    var receivable = collectibleReceivable(company, invoiceId, saleId, request);
    BigDecimal amount = request.total();
    BigDecimal balance = receivable.balance().subtract(amount);
    if (balance.signum() < 0) {
      throw new InvoicePaymentConflict("Payment exceeds the outstanding balance");
    }

    var admission = authority.admit(company, operator, payment, request);
    if (admission == null
        || admission.shiftId() == null
        || admission.accountsByLine() == null
        || IntStream.range(0, request.lines().size())
            .anyMatch(index -> admission.accountsByLine().get(index) == null)) {
      throw new PaymentAdmissionUnavailable();
    }
    Instant registeredAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    var lines =
        recordLines(company, payment, invoiceId, operator, request, admission, registeredAt);
    receivables.applyPayment(receivable, balance);
    var result =
        new InvoicePayment.Result(
            payment,
            invoiceId,
            receivable.id(),
            operator,
            admission.shiftId(),
            registeredAt,
            amount,
            balance,
            "DOP",
            lines,
            request.notes());
    complete(result);
    audit.append(
        company,
        operator,
        SalesAudit.Operation.INVOICE_PAYMENT_REGISTERED,
        payment,
        balance.signum() == 0 ? "PAID" : "PARTIALLY_PAID");
    return result;
  }

  private Receivable collectibleReceivable(
      UUID company, UUID invoiceId, UUID saleId, InvoicePayment.Request request) {
    String clientName =
        jdbc.sql(
                """
                SELECT i.client_name
                  FROM sales.internal_invoices i
                  JOIN sales.sales s ON s.company_id = i.company_id AND s.id = i.sale_id
                 WHERE i.company_id = :company AND i.id = :invoice
                   AND i.status = 'ISSUED' AND s.status = 'REGISTERED'
                """)
            .param("company", company)
            .param("invoice", invoiceId)
            .query(String.class)
            .optional()
            .orElseThrow(() -> new InvoicePaymentConflict("Invoice is inactive"));
    var receivable =
        receivables
            .lockBySale(company, saleId)
            .orElseThrow(() -> new InvoicePaymentConflict("Invoice has no receivable"));
    if (receivable.status() != ReceivableStatus.OPEN
        && receivable.status() != ReceivableStatus.PARTIALLY_PAID) {
      throw new InvoicePaymentConflict("Invoice has no collectible balance");
    }
    if (!clientName.equals(request.clientName())
        || receivable.balance().compareTo(request.balance()) != 0) {
      throw new InvoicePaymentConflict("Invoice client or displayed balance has changed");
    }
    return receivable;
  }

  private List<InvoicePayment.AppliedLine> recordLines(
      UUID company,
      UUID payment,
      UUID invoiceId,
      UUID operator,
      InvoicePayment.Request request,
      PaymentAdmission.Admission admission,
      Instant registeredAt) {
    String traceId = RequestCorrelation.current();
    List<InvoicePayment.AppliedLine> lines = new ArrayList<>();
    for (int index = 0; index < request.lines().size(); index++) {
      var line = request.lines().get(index);
      var applied =
          new InvoicePayment.AppliedLine(
              UUID.randomUUID(), line.method(), line.amount(), line.reference());
      lines.add(applied);
      outbox.enqueue(
          company,
          new PaymentEventV1(
              UUID.randomUUID(),
              PaymentEventTypeV1.ReceivablePaymentRegisteredV1,
              PaymentEventV1.VERSION,
              registeredAt,
              company,
              applied.id(),
              traceId,
              payment,
              admission.shiftId(),
              admission.accountsByLine().get(index),
              PaymentMethodV1.valueOf(line.method().name()),
              line.amount(),
              "DOP",
              null,
              null,
              invoiceId,
              operator));
      jdbc.sql(
              """
              INSERT INTO sales.invoice_payment_lines (id, payment_id, position, method, amount, reference, account_id)
              VALUES (:id, :payment, :position, :method, :amount, :reference, :account)
              """)
          .param("id", applied.id())
          .param("payment", payment)
          .param("position", index)
          .param("method", line.method().name())
          .param("amount", line.amount())
          .param("reference", line.reference())
          .param("account", admission.accountsByLine().get(index))
          .update();
    }

    return List.copyOf(lines);
  }

  private void complete(InvoicePayment.Result result) {
    jdbc.sql(
            """
            UPDATE sales.invoice_payments
               SET response_body = :response, shift_id = :shift, receivable_id = :receivable,
                   registered_at = :at, amount = :amount, balance = :balance, notes = :notes
             WHERE id = :id
            """)
        .param("response", json.writeValueAsString(result))
        .param("shift", result.shiftId())
        .param("receivable", result.receivableId())
        .param("at", Timestamp.from(result.registeredAt()))
        .param("amount", result.amount())
        .param("balance", result.balance())
        .param("notes", result.notes())
        .param("id", result.paymentId())
        .update();
  }

  private InvoicePayment.Result replay(
      UUID company, UUID operator, UUID invoice, String key, String request, String fingerprint) {
    return jdbc.sql(
            """
            SELECT invoice_id = :invoice
                   AND COALESCE(request_fingerprint = :fingerprint,
                                request_body = CAST(:request AS jsonb)) AS matches, response_body
              FROM sales.invoice_payments
             WHERE company_id = :company AND operator_id = :operator AND idempotency_key = :key
            """)
        .param("invoice", invoice)
        .param("request", request)
        .param("fingerprint", fingerprint)
        .param("company", company)
        .param("operator", operator)
        .param("key", key)
        .query((row, index) -> {
          if (!row.getBoolean("matches")) {
            throw new IdempotencyConflict();
          }
          return json.readValue(row.getString("response_body"), InvoicePayment.Result.class);
        })
        .single();
  }
}
