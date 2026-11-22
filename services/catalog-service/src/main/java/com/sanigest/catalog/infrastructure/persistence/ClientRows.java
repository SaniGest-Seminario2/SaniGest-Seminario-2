package com.sanigest.catalog.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface ClientRows extends Repository<ClientRow, UUID> {
  String FILTER =
      """
      FROM catalog.clients
      WHERE company_id = :companyId
        AND (:status = '' OR status = :status)
        AND (:pattern = ''
          OR lower(name) LIKE lower(:pattern) ESCAPE '!'
          OR lower(tax_id) LIKE lower(:pattern) ESCAPE '!')
      """;

  <S extends ClientRow> S saveAndFlush(S row);

  @Query(
      value =
          """
          SELECT id, name, tax_id AS "taxId", status
          """
              + FILTER
              + """
              ORDER BY name ASC, id ASC
              LIMIT :size OFFSET :offset
              """,
      nativeQuery = true)
  List<ListedClient> search(
      @Param("companyId") UUID companyId,
      @Param("pattern") String pattern,
      @Param("status") String status,
      @Param("size") int size,
      @Param("offset") long offset);

  @Query(value = "SELECT count(*) " + FILTER, nativeQuery = true)
  long countMatching(
      @Param("companyId") UUID companyId,
      @Param("pattern") String pattern,
      @Param("status") String status);

  interface ListedClient {
    UUID getId();

    String getName();

    String getTaxId();

    String getStatus();
  }
}
