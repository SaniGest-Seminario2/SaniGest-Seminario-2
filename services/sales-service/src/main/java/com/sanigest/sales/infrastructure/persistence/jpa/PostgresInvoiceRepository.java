package com.sanigest.sales.infrastructure.persistence.jpa;

import com.sanigest.sales.application.exception.IdempotencyConflict;
import com.sanigest.sales.application.exception.InvoiceConflict;
import com.sanigest.sales.application.exception.SaleNotFound;
import com.sanigest.sales.application.port.out.persistence.InvoiceRepository;
import com.sanigest.sales.domain.model.InternalInvoice;
import com.sanigest.sales.domain.model.Sale;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PostgresInvoiceRepository implements InvoiceRepository {
  private final JdbcTemplate jdbc;
  private final SaleRows sales;
  private final EntityManager entityManager;

  PostgresInvoiceRepository(JdbcTemplate jdbc, SaleRows sales, EntityManager entityManager) {
    this.jdbc = jdbc;
    this.sales = sales;
    this.entityManager = entityManager;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public String issueForNewSale(
      Sale sale,
      int year,
      Function<String, InternalInvoice> create,
      Function<InternalInvoice, String> response) {
    entityManager.flush();
    var invoice = create.apply(allocateNumber(sale.companyId(), year));
    String body = response.apply(invoice);
    insert(invoice, null, body);
    return body;
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public String issue(
      Request request,
      BiFunction<Sale, Supplier<String>, InternalInvoice> create,
      Function<InternalInvoice, String> response) {
    // Serialize requests across sales for the same actor and key. Hash collisions only
    // cause extra waiting, because replay and uniqueness use the full tuple.
    jdbc.query(
        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
        row -> {},
        "sales.issue-invoice:"
            + request.companyId()
            + ":"
            + request.actorId()
            + ":"
            + request.key());

    var replays =
        jdbc.query(
            """
            SELECT sale_id, response_body FROM sales.internal_invoices
            WHERE company_id = ? AND issued_by = ? AND idempotency_key = ?
            """,
            (row, index) -> {
              if (!request.saleId().equals(row.getObject("sale_id", UUID.class))) {
                throw new IdempotencyConflict();
              }
              return row.getString("response_body");
            },
            request.companyId(),
            request.actorId(),
            request.key());
    if (!replays.isEmpty()) {
      return replays.getFirst();
    }

    sales.lockSale(request.companyId(), request.saleId()).orElseThrow(SaleNotFound::new);
    Sale sale = loadSource(request);
    if (sales.hasIssuedInvoice(request.companyId(), sale.id())) {
      throw new InvoiceConflict();
    }

    InternalInvoice invoice =
        create.apply(sale, () -> allocateNumber(request.companyId(), request.year()));
    String body = response.apply(invoice);
    insert(invoice, request.key(), body);
    return body;
  }

  private String allocateNumber(UUID companyId, int year) {
    long sequence =
        jdbc.queryForObject(
            """
            INSERT INTO sales.invoice_number_sequences AS counter (company_id, issuance_year, last_number)
            VALUES (?, ?, 1)
            ON CONFLICT (company_id, issuance_year)
            DO UPDATE SET last_number = counter.last_number + 1
            RETURNING last_number
            """,
            Long.class,
            companyId,
            year);
    return String.format(Locale.ROOT, "FAC-%04d-%03d", year, sequence);
  }

  private Sale loadSource(Request request) {
    try {
      return sales
          .findByCompanyIdAndId(request.companyId(), request.saleId())
          .map(SaleRow::toDomain)
          .orElseThrow(SaleNotFound::new);
    } catch (IllegalArgumentException invalidSnapshot) {
      throw new InvoiceConflict();
    }
  }

  private void insert(InternalInvoice invoice, String key, String body) {
    jdbc.update(
        """
        INSERT INTO sales.internal_invoices
          (id, company_id, sale_id, sale_date, number, document_type, fiscal_classification,
           status, issued_at, issued_by, company_name, client_id, client_name, total, currency,
           idempotency_key, response_body)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        invoice.id(),
        invoice.company().id(),
        invoice.saleId(),
        invoice.saleDate(),
        invoice.number(),
        invoice.documentType().name(),
        invoice.fiscalClassification().name(),
        invoice.status().name(),
        OffsetDateTime.ofInstant(invoice.issuedAt(), ZoneOffset.UTC),
        invoice.issuedBy(),
        invoice.company().name(),
        invoice.client().id(),
        invoice.client().name(),
        invoice.total(),
        invoice.currency(),
        key,
        body);

    var lines = invoice.lines();
    for (int position = 0; position < lines.size(); position++) {
      var line = lines.get(position);
      jdbc.update(
          """
          INSERT INTO sales.internal_invoice_lines
            (invoice_id, position, source_line_id, catalog_item_id, item_name, item_description,
             unit_price, quantity, subtotal, discount, tax_rate)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          invoice.id(),
          position,
          line.id(),
          line.catalogItemId(),
          line.itemName(),
          line.itemDescription(),
          line.unitPrice(),
          line.quantity(),
          line.subtotal(),
          line.discount(),
          line.taxRate());
    }
  }
}
