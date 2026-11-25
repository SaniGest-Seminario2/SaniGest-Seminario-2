package com.sanigest.catalog.infrastructure.persistence.jpa;

import com.sanigest.catalog.domain.enums.SupplierPaymentTerms;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.enums.SupplierSupplyType;
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
        AND (:#{#filter.status()} = '' OR status = :#{#filter.status()})
        AND (:#{#filter.supplyType()} = '' OR supply_type = :#{#filter.supplyType()} OR supply_type = 'BOTH')
        AND (:#{#filter.paymentTerms()} = '' OR payment_terms = :#{#filter.paymentTerms()}
          OR payment_terms = 'BOTH')
        AND (CAST(:#{#filter.categoryId()} AS uuid) IS NULL OR category_id = :#{#filter.categoryId()})
        AND (:#{#filter.pattern()} = ''
          OR lower(name) LIKE lower(:#{#filter.pattern()}) ESCAPE '!'
          OR lower(tax_id) LIKE lower(:#{#filter.pattern()}) ESCAPE '!'
          OR lower(contact) LIKE lower(:#{#filter.pattern()}) ESCAPE '!')
      """;

  <S extends SupplierRow> S saveAndFlush(S row);

  Optional<SupplierRow> findByCompanyIdAndId(UUID companyId, UUID id);

  @Query(
      value =
          """
          SELECT EXISTS (SELECT 1 FROM catalog.suppliers
          WHERE company_id = :companyId AND id <> :id AND tax_id ~ '^[0-9][0-9 -]*[0-9]$'
            AND replace(replace(tax_id, ' ', ''), '-', '') = :taxId)
          """,
      nativeQuery = true)
  boolean hasDomesticDuplicate(UUID companyId, UUID id, String taxId);

  @Query(
      value =
          """
          SELECT EXISTS (SELECT 1 FROM catalog.supplier_items si
          JOIN catalog.catalog_items i ON i.company_id = si.company_id AND i.id = si.catalog_item_id
          WHERE si.company_id = :companyId AND si.supplier_id = :id AND si.valid_until IS NULL
            AND :supplyType NOT IN ('BOTH', i.type))
          """,
      nativeQuery = true)
  boolean hasIncompatibleItems(UUID companyId, UUID id, String supplyType);

  @Modifying(clearAutomatically = true)
  @Query(
      """
      UPDATE SupplierRow SET name = :#{#details.name()}, taxId = :#{#details.taxId()}, email = :#{#details.email()},
        phone = :#{#details.phone()}, contact = :#{#details.contact()},
        address = :#{#details.profile().address()},
        supplyType = :#{#details.profile().supplyType()},
        paymentTerms = :#{#details.profile().paymentTerms()},
        categoryId = :#{#details.profile().categoryId()},
        creditDays = :#{#details.profile().creditDays()},
        observations = :#{#details.profile().observations()}, status = :status, revision = revision + 1
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
          SELECT id, name, tax_id AS "taxId", status, contact, phone, supply_type AS "supplyType",
              payment_terms AS "paymentTerms", category_id AS "categoryId"
          """
              + FILTER
              + """
              ORDER BY CASE WHEN :#{#filter.descending()} THEN name END DESC, name ASC, id ASC
              LIMIT :size OFFSET :offset
              """,
      nativeQuery = true)
  List<ListedSupplier> search(
      @Param("companyId") UUID companyId,
      @Param("filter") SearchFilter filter,
      @Param("size") int size,
      @Param("offset") long offset);

  @Query(value = "SELECT count(*) " + FILTER, nativeQuery = true)
  long countMatching(@Param("companyId") UUID companyId, @Param("filter") SearchFilter filter);

  record SearchFilter(
      String pattern,
      String status,
      String supplyType,
      String paymentTerms,
      UUID categoryId,
      boolean descending) {}

  interface ListedSupplier {
    UUID getId();

    String getName();

    String getTaxId();

    String getStatus();

    String getContact();

    String getPhone();

    SupplierSupplyType getSupplyType();

    SupplierPaymentTerms getPaymentTerms();

    UUID getCategoryId();
  }
}
