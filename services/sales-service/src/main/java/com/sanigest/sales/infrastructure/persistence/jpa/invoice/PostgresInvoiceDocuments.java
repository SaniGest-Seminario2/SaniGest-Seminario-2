package com.sanigest.sales.infrastructure.persistence.jpa.invoice;

import com.sanigest.sales.application.dto.invoice.InvoicePage;
import com.sanigest.sales.application.exception.invoice.InvoiceConflict;
import com.sanigest.sales.application.port.out.persistence.invoice.InvoiceDocuments;
import com.sanigest.sales.application.port.out.persistence.sale.SaleRepository;
import com.sanigest.sales.domain.model.sale.SaleCancellation;
import com.sanigest.sales.infrastructure.persistence.jpa.sale.SaleRows;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PostgresInvoiceDocuments implements InvoiceDocuments {
  private static final String FROM =
      """
      FROM sales.internal_invoices i
      JOIN sales.sales s ON s.company_id = i.company_id AND s.id = i.sale_id
      LEFT JOIN sales.receivables r ON r.company_id = i.company_id AND r.sale_id = i.sale_id
      """
          + SaleRows.PAYMENT_PROJECTION
          + """
          CROSS JOIN LATERAL (
            SELECT CASE WHEN payment.status = 'OPEN' THEN 'ISSUED' ELSE payment.status END AS status
          ) invoice_payment
          """;
  private static final String FILTER =
      """
      WHERE i.company_id = :company
        AND (i.number ILIKE :pattern ESCAPE '!' OR i.client_name ILIKE :pattern ESCAPE '!')
        AND (:status IN ('', 'ALL') OR
             invoice_payment.status = :status)
        AND (NOT :collectible OR
            (i.status = 'ISSUED' AND s.status = 'REGISTERED' AND r.status IN ('OPEN', 'PARTIALLY_PAID')
             AND payment.outstanding > 0))
      """;
  // The issued snapshot never changes, including the exact JSON used for POST replays.
  private static final String CURRENT_DOCUMENT =
      """
      SELECT (i.response_body::jsonb || jsonb_strip_nulls(jsonb_build_object(
          'status', i.status, 'issueDate', i.sale_date, 'mode', s.mode,
          'paymentStatus', invoice_payment.status,
          'outstandingBalance', CAST(round(payment.outstanding, 2) AS text),
          'collectedAmount', CAST(round(payment.collected, 2) AS text),
          'cancelledAt', i.cancelled_at, 'cancelledBy', i.cancelled_by,
          'cancelReason', i.cancel_reason)))::text
      """;
  private final JdbcClient jdbc;
  private final SaleRepository sales;

  PostgresInvoiceDocuments(JdbcClient jdbc, SaleRepository sales) {
    this.jdbc = jdbc;
    this.sales = sales;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public InvoicePage list(UUID company, Search search) {
    var page =
        filter(
            CURRENT_DOCUMENT
                + FROM
                + FILTER
                + " ORDER BY i.sale_date DESC, i.id DESC LIMIT :limit OFFSET :offset",
            company,
            search);
    var documents =
        page.param("limit", search.limit())
            .param("offset", search.offset())
            .query(String.class)
            .list();
    long total =
        search.countTotal()
            ? filter("SELECT count(*) " + FROM + FILTER, company, search).query(Long.class).single()
            : documents.size();
    return new InvoicePage(documents, total);
  }

  private JdbcClient.StatementSpec filter(String sql, UUID company, Search search) {
    return jdbc.sql(sql)
        .param("company", company)
        .param("pattern", search.pattern())
        .param("status", search.status())
        .param("collectible", search.collectible());
  }

  @Override
  public Optional<String> find(UUID company, UUID invoice) {
    return jdbc.sql(CURRENT_DOCUMENT + FROM + " WHERE i.company_id = :company AND i.id = :id")
        .param("company", company)
        .param("id", invoice)
        .query(String.class)
        .optional();
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Optional<String> cancel(UUID company, UUID invoice, SaleCancellation cancellation) {
    var sale =
        jdbc.sql(
                """
                SELECT sale_id FROM sales.internal_invoices WHERE company_id = :company AND id = :id
                """)
            .param("company", company)
            .param("id", invoice)
            .query(UUID.class)
            .optional();
    if (sale.isEmpty()) {
      return Optional.empty();
    }
    UUID saleId = sale.orElseThrow();
    sales.cancel(company, saleId, cancellation).orElseThrow(InvoiceConflict::new);
    return find(company, invoice);
  }
}
