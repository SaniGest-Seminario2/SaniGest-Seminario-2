package com.sanigest.catalog.infrastructure.persistence.jpa;

import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.model.SupplierDetails;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface SupplierRows extends Repository<SupplierRow, UUID> {
  String FILTER =
      """
      FROM catalog.suppliers
      WHERE company_id = :companyId
        AND (:status = '' OR status = :status)
        AND (:pattern = ''
          OR lower(name) LIKE lower(:pattern) ESCAPE '!'
          OR lower(tax_id) LIKE lower(:pattern) ESCAPE '!')
      """;

  <S extends SupplierRow> S saveAndFlush(S row);

  Optional<SupplierRow> findByCompanyIdAndId(UUID companyId, UUID id);

  @Modifying(clearAutomatically = true)
  @Query(
      """
      UPDATE SupplierRow SET name = :#{#details.name()}, taxId = :#{#details.taxId()}, email = :#{#details.email()},
        phone = :#{#details.phone()}, contact = :#{#details.contact()}, status = :status, revision = revision + 1
      WHERE companyId = :companyId AND id = :id AND revision = :revision
      """)
  int update(
      @Param("companyId") UUID companyId,
      @Param("id") UUID id,
      @Param("revision") long revision,
      @Param("details") SupplierDetails details,
      @Param("status") SupplierStatus status);

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
  List<ListedSupplier> search(
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

  interface ListedSupplier {
    UUID getId();

    String getName();

    String getTaxId();

    String getStatus();
  }
}
