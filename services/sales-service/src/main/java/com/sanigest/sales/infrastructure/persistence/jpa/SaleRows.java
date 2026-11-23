package com.sanigest.sales.infrastructure.persistence.jpa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface SaleRows extends Repository<SaleRow, UUID> {
  @EntityGraph(attributePaths = "lines")
  Optional<SaleRow> findByCompanyIdAndId(UUID companyId, UUID id);

  String FILTER =
      """
      FROM sales.sales
      WHERE company_id = :companyId
        AND sale_date BETWEEN :fromDate AND :toDate
        AND (:status = '' OR status = :status)
        AND (:pattern = '' OR lower(client_name) LIKE lower(:pattern) ESCAPE '!')
      """;

  @Query(value = "SELECT count(*) " + FILTER, nativeQuery = true)
  long countMatching(
      UUID companyId, String pattern, String status, LocalDate fromDate, LocalDate toDate);

  @Query(
      value =
          """
          SELECT id, code, client_id AS "clientId", client_name AS "clientName",
                 sale_date AS "saleDate", mode, status, total
          """
              + FILTER
              + """
              ORDER BY sale_date DESC, id DESC
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
  }
}
