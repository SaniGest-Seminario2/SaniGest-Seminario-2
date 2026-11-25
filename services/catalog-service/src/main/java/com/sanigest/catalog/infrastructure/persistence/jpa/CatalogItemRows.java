package com.sanigest.catalog.infrastructure.persistence.jpa;

import com.sanigest.catalog.application.dto.CatalogItemSearchCriteria;
import com.sanigest.catalog.domain.model.CatalogItem;
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
        AND (CAST(:#{#criteria.active()} AS boolean) IS NULL OR active = :#{#criteria.active()})
        AND (CAST(:#{#criteria.categoryId()} AS uuid) IS NULL OR category_id = :#{#criteria.categoryId()})
        AND (CAST(:#{#criteria.availableForSale()} AS boolean) IS NULL
          OR available_for_sale = :#{#criteria.availableForSale()})
        AND (CAST(:#{#criteria.availableForPurchase()} AS boolean) IS NULL
          OR available_for_purchase = :#{#criteria.availableForPurchase()})
        AND (:pattern = '' OR name ILIKE :pattern ESCAPE '!'
            OR sku ILIKE :pattern ESCAPE '!' OR barcode ILIKE :pattern ESCAPE '!')
      """;

  <S extends CatalogItemRow> S saveAndFlush(S row);

  Optional<CatalogItemRow> findByCompanyIdAndId(UUID companyId, UUID id);

  @Modifying(clearAutomatically = true)
  @Query(
      """
      UPDATE CatalogItemRow SET name = :#{#item.name()},
        description = :#{#item.description()}, price = :#{#item.price()},
        active = :#{#item.active()},
        sku = :#{#item.profile().sku()},
        barcode = :#{#item.profile().barcode()},
        categoryId = :#{#item.profile().categoryId()},
        unitCode = :#{#item.profile().unitCode()},
        estimatedCost = :#{#item.profile().estimatedCost()},
        taxClassificationCode = :#{#item.profile().taxClassificationCode()},
        availableForSale = :#{#item.profile().availableForSale()},
        availableForPurchase = :#{#item.profile().availableForPurchase()}, revision = revision + 1
      WHERE companyId = :companyId AND id = :id AND revision = :revision
      """)
  int update(
      @Param("companyId") UUID companyId,
      @Param("id") UUID id,
      @Param("revision") long revision,
      @Param("item") CatalogItem item);

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
      @Param("criteria") CatalogItemSearchCriteria criteria,
      @Param("size") int size,
      @Param("offset") long offset);

  @Query(value = "SELECT count(*) " + FILTER, nativeQuery = true)
  long countMatching(
      @Param("companyId") UUID companyId,
      @Param("type") String type,
      @Param("pattern") String pattern,
      @Param("criteria") CatalogItemSearchCriteria criteria);
}
