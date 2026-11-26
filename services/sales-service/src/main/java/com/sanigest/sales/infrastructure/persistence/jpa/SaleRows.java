package com.sanigest.sales.infrastructure.persistence.jpa;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface SaleRows extends Repository<SaleRow, UUID> {
  @EntityGraph(attributePaths = "lines")
  Optional<SaleRow> findByCompanyIdAndId(UUID companyId, UUID id);

  @Query(
      value =
          """
          SELECT id FROM sales.sales WHERE company_id = :companyId AND id = :saleId FOR UPDATE
          """,
      nativeQuery = true)
  Optional<UUID> lockSale(UUID companyId, UUID saleId);

  @Query(
      value =
          """
          SELECT EXISTS (
              SELECT 1 FROM sales.internal_invoices
              WHERE company_id = :companyId AND sale_id = :saleId AND status = 'ISSUED'
          )
          """,
      nativeQuery = true)
  boolean hasIssuedInvoice(UUID companyId, UUID saleId);

  @Modifying
  @Query(
      value =
          """
          UPDATE sales.sales
             SET status = 'CANCELLED', cancelled_by = :actor, cancelled_at = :at, cancel_reason = :reason
           WHERE company_id = :companyId AND id = :saleId AND status = 'REGISTERED'
          """,
      nativeQuery = true)
  int recordCancellation(UUID companyId, UUID saleId, UUID actor, Instant at, String reason);

  String DOCUMENT_JOINS =
      """
      LEFT JOIN sales.internal_invoices i
        ON i.company_id = s.company_id AND i.sale_id = s.id AND i.status = 'ISSUED'
      LEFT JOIN sales.receivables r ON r.company_id = s.company_id AND r.sale_id = s.id
      """;

  @Query(
      value =
          """
          SELECT i.id AS "invoiceId", i.number AS "invoiceNumber",
                 r.due_date AS "dueDate",
                 r.status AS "paymentStatus", r.balance AS "balance",
                 r.total_amount - r.balance AS "collected"
          FROM sales.sales s
          """
              + DOCUMENT_JOINS
              + " WHERE s.company_id = :companyId AND s.id = :saleId",
      nativeQuery = true)
  Optional<SaleFinancialRow> financials(UUID companyId, UUID saleId);

  interface SaleFinancialRow {
    UUID getInvoiceId();

    String getInvoiceNumber();

    LocalDate getDueDate();

    String getPaymentStatus();

    BigDecimal getBalance();

    BigDecimal getCollected();
  }

  String FILTER =
      """
      WHERE s.company_id = :companyId
        AND s.sale_date BETWEEN :fromDate AND :toDate
        AND (:status = '' OR s.status = :status)
        AND (:pattern = '' OR lower(s.client_name) LIKE lower(:pattern) ESCAPE '!')
      """;

  @Query(value = "SELECT count(*) FROM sales.sales s " + FILTER, nativeQuery = true)
  long countMatching(
      UUID companyId, String pattern, String status, LocalDate fromDate, LocalDate toDate);

  @Query(
      value =
          """
          SELECT s.id, s.code, s.client_id AS "clientId", s.client_name AS "clientName",
                 s.sale_date AS "saleDate", s.mode, s.status, s.total,
                 i.number AS "invoiceNumber", r.status AS "paymentStatus"
          FROM sales.sales s
          """
              + DOCUMENT_JOINS
              + FILTER
              + """
              ORDER BY s.sale_date DESC, s.id DESC
              LIMIT :size OFFSET :offset
              """,
      nativeQuery = true)
  List<ListedSale> search(
      UUID companyId,
      String pattern,
      String status,
      LocalDate fromDate,
      LocalDate toDate,
      int size,
      long offset);

  interface ListedSale {
    UUID getId();

    String getCode();

    UUID getClientId();

    String getClientName();

    LocalDate getSaleDate();

    String getMode();

    String getStatus();

    BigDecimal getTotal();

    String getInvoiceNumber();

    String getPaymentStatus();
  }
}
