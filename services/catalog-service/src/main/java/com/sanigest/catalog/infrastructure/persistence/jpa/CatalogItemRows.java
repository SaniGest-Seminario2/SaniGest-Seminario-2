package com.sanigest.catalog.infrastructure.persistence.jpa;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface CatalogItemRows extends Repository<CatalogItemRow, UUID> {
  String FILTER =
      """
      FROM catalog.catalog_items
      WHERE company_id = :companyId
        AND (:type = '' OR type = :type)
        AND (:ignoreActive = true OR active = :active)
        AND (:pattern = '' OR lower(name) LIKE lower(:pattern) ESCAPE '!')
      """;

  <S extends CatalogItemRow> S save(S row);

  Optional<CatalogItemRow> findByCompanyIdAndId(UUID companyId, UUID id);

  @Modifying(clearAutomatically = true)
  @Query(
      """
      UPDATE CatalogItemRow SET name = :name, description = :description, price = :price,
        active = :active, revision = revision + 1
      WHERE companyId = :companyId AND id = :id AND revision = :revision
      """)
  int update(
      @Param("companyId") UUID companyId,
      @Param("id") UUID id,
      @Param("revision") long revision,
      @Param("name") String name,
      @Param("description") String description,
      @Param("price") BigDecimal price,
      @Param("active") boolean active);

  @Query(
      value =
          "SELECT * "
              + FILTER
              + """
              ORDER BY name ASC, id ASC
              LIMIT :size OFFSET :offset
              """,
      nativeQuery = true)
  List<CatalogItemRow> search(
      @Param("companyId") UUID companyId,
      @Param("type") String type,
      @Param("pattern") String pattern,
      @Param("ignoreActive") boolean ignoreActive,
      @Param("active") boolean active,
      @Param("size") int size,
      @Param("offset") long offset);

  @Query(value = "SELECT count(*) " + FILTER, nativeQuery = true)
  long countMatching(
      @Param("companyId") UUID companyId,
      @Param("type") String type,
      @Param("pattern") String pattern,
      @Param("ignoreActive") boolean ignoreActive,
      @Param("active") boolean active);
}
