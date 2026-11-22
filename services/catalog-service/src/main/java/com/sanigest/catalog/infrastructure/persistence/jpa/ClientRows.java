package com.sanigest.catalog.infrastructure.persistence.jpa;

import com.sanigest.catalog.domain.enums.ClientStatus;
import com.sanigest.catalog.domain.model.ClientDetails;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
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

  Optional<ClientRow> findByCompanyIdAndId(UUID companyId, UUID id);

  @Modifying(clearAutomatically = true)
  @Query(
      """
      UPDATE ClientRow SET name = :#{#details.name()}, taxId = :#{#details.taxId()}, email = :#{#details.email()},
        phone = :#{#details.phone()}, address = :#{#details.address()}, status = :status, revision = revision + 1
      WHERE companyId = :companyId AND id = :id AND revision = :revision
      """)
  int update(
      @Param("companyId") UUID companyId,
      @Param("id") UUID id,
      @Param("revision") long revision,
      @Param("details") ClientDetails details,
      @Param("status") ClientStatus status);

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
