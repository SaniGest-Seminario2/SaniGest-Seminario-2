package com.sanigest.catalog.infrastructure.persistence.jpa;

import com.sanigest.catalog.domain.enums.ClientStatus;
import com.sanigest.catalog.domain.model.ClientDetails;
import com.sanigest.catalog.domain.model.ClientProfile;
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
      FROM catalog.clients client
      LEFT JOIN catalog.client_categories category
        ON category.company_id = client.company_id AND category.id = client.category_id
      WHERE client.company_id = :companyId
        AND (:#{#filter.status()} = '' OR client.status = :#{#filter.status()})
        AND (:#{#filter.type()} = '' OR client.client_type = :#{#filter.type()})
        AND (:#{#filter.category()} = '' OR client.category_id = CAST(NULLIF(:#{#filter.category()}, '') AS uuid))
        AND (:#{#filter.terms()} = '' OR client.payment_terms = :#{#filter.terms()}
          OR (client.payment_terms = 'BOTH' AND :#{#filter.terms()} IN ('CASH', 'CREDIT')))
        AND (:#{#filter.pattern()} = ''
          OR lower(client.name) LIKE lower(:#{#filter.pattern()}) ESCAPE '!'
          OR lower(client.tax_id) LIKE lower(:#{#filter.pattern()}) ESCAPE '!'
          OR client.tax_id_key LIKE :#{#filter.pattern()} ESCAPE '!'
          OR lower(client.email) LIKE lower(:#{#filter.pattern()}) ESCAPE '!'
          OR lower(client.phone) LIKE lower(:#{#filter.pattern()}) ESCAPE '!'
          OR lower(client.alternate_phone) LIKE lower(:#{#filter.pattern()}) ESCAPE '!')
      """;

  <S extends ClientRow> S saveAndFlush(S row);

  Optional<ClientRow> findByCompanyIdAndId(UUID companyId, UUID id);

  @Query(
      value =
          """
          SELECT EXISTS (
            SELECT 1 FROM catalog.clients
            WHERE company_id = :companyId AND id <> :id
              AND tax_id_key = catalog.client_tax_id_key(:taxId)
          )
          """,
      nativeQuery = true)
  boolean duplicateTaxId(UUID companyId, UUID id, String taxId);

  @Modifying(clearAutomatically = true)
  @Query(
      """
      UPDATE ClientRow SET name = :#{#details.name()}, taxId = :#{#details.taxId()},
        email = :#{#details.email()}, phone = :#{#details.phone()}, address = :#{#details.address()},
        status = :status, revision = revision + 1, enforceTaxKey = :enforceTaxKey,
        clientType = :#{#profile?.clientType()},
        alternatePhone = :#{#profile?.alternatePhone()},
        countryCode = :#{#profile?.countryCode()},
        provinceCode = :#{#profile?.provinceCode()},
        municipalityCode = :#{#profile?.municipalityCode()},
        preferredReceiptType = :#{#profile?.preferredReceiptType()},
        paymentTerms = :#{#profile?.paymentTerms()},
        categoryId = :#{#profile?.categoryId()},
        creditLimit = :#{#profile?.creditLimit()},
        observations = :#{#profile?.observations()}
      WHERE companyId = :companyId AND id = :id AND revision = :revision
      """)
  int update(
      @Param("companyId") UUID companyId,
      @Param("id") UUID id,
      @Param("revision") long revision,
      @Param("details") ClientDetails details,
      @Param("profile") ClientProfile profile,
      @Param("status") ClientStatus status,
      @Param("enforceTaxKey") boolean enforceTaxKey);

  @Query(
      value =
          """
          SELECT client.id, client.name, client.tax_id AS "taxId", client.status, client.phone,
            client.client_type AS "clientType", client.payment_terms AS "paymentTerms",
            client.category_id AS "categoryId", category.name AS "categoryName"
          """
              + FILTER
              + """
              ORDER BY CASE WHEN :descending THEN client.name END DESC,
                       CASE WHEN NOT :descending THEN client.name END ASC, client.id ASC
              LIMIT :size OFFSET :offset
              """,
      nativeQuery = true)
  List<ListedClient> search(
      @Param("companyId") UUID companyId,
      @Param("filter") Filter filter,
      @Param("descending") boolean descending,
      @Param("size") int size,
      @Param("offset") long offset);

  @Query(value = "SELECT count(*) " + FILTER, nativeQuery = true)
  long countMatching(@Param("companyId") UUID companyId, @Param("filter") Filter filter);

  record Filter(String pattern, String status, String type, String category, String terms) {}

  interface ListedClient {
    UUID getId();

    String getName();

    String getTaxId();

    String getStatus();

    String getPhone();

    String getClientType();

    String getPaymentTerms();

    UUID getCategoryId();

    String getCategoryName();
  }
}
