package com.sanigest.catalog.infrastructure.persistence.jpa;

import static com.sanigest.catalog.application.exception.CatalogItemAccessException.Reason.CATALOG_ITEM_NOT_FOUND;

import com.sanigest.catalog.application.dto.CatalogItemCommercialData;
import com.sanigest.catalog.application.dto.CatalogItemHistory;
import com.sanigest.catalog.application.dto.CatalogItemReferences.Tax;
import com.sanigest.catalog.application.dto.CatalogItemReferences.Unit;
import com.sanigest.catalog.application.dto.CatalogItemSummary;
import com.sanigest.catalog.application.exception.CatalogItemAccessException;
import com.sanigest.catalog.application.exception.DuplicateCatalogItemCode;
import com.sanigest.catalog.application.port.out.persistence.CatalogItemCommercialRepository;
import com.sanigest.catalog.domain.model.CatalogItemCategory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCatalogItemCommercialRepository implements CatalogItemCommercialRepository {
  private static final String RATE_DATE =
      "r.effective_from <= :date AND (r.effective_until IS NULL OR :date < r.effective_until)";
  private final JdbcClient jdbc;

  JdbcCatalogItemCommercialRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void initializeCategories(UUID companyId) {
    jdbc.sql(
            """
            INSERT INTO catalog.catalog_item_categories (company_id, code, name)
            VALUES (:company, 'OFFICE_SUPPLIES', 'Materiales de oficina'),
                (:company, 'TECHNOLOGY', 'Tecnología'), (:company, 'MERCHANDISE', 'Mercancías'),
                (:company, 'FOOD_BEVERAGES', 'Alimentos y bebidas'), (:company, 'OTHER', 'Otros')
            ON CONFLICT (company_id, code) DO NOTHING
            """)
        .param("company", companyId)
        .update();
  }

  @Override
  public List<CatalogItemCategory> categories(UUID companyId) {
    return jdbc.sql(
            """
            SELECT * FROM catalog.catalog_item_categories WHERE company_id = :company ORDER BY name, id
            """)
        .param("company", companyId)
        .query((row, index) -> category(row))
        .list();
  }

  @Override
  public CatalogItemCategory lockCategory(UUID companyId, UUID id, boolean exclusive) {
    return jdbc.sql(
            """
            SELECT * FROM catalog.catalog_item_categories WHERE company_id = :company AND id = :id
            """
                + (exclusive ? " FOR NO KEY UPDATE" : " FOR SHARE"))
        .param("company", companyId)
        .param("id", id)
        .query((row, index) -> category(row))
        .optional()
        .orElseThrow(() -> new CatalogItemAccessException(CATALOG_ITEM_NOT_FOUND));
  }

  @Override
  public void insertCategory(CatalogItemCategory category) {
    try {
      jdbc.sql(
              """
              INSERT INTO catalog.catalog_item_categories (company_id, id, code, name)
              VALUES (:company, :id, :code, :name)
              """)
          .param("company", category.companyId())
          .param("id", category.id())
          .param("code", category.code())
          .param("name", category.name())
          .update();
    } catch (DuplicateKeyException duplicate) {
      throw new DuplicateCatalogItemCode("DUPLICATE_CATEGORY_CODE");
    }
  }

  @Override
  public void updateCategory(CatalogItemCategory category) {
    jdbc.sql(
            """
            UPDATE catalog.catalog_item_categories SET name = :name, active = :active
            WHERE company_id = :company AND id = :id
            """)
        .param("company", category.companyId())
        .param("id", category.id())
        .param("name", category.name())
        .param("active", category.active())
        .update();
  }

  private static CatalogItemCategory category(ResultSet row) throws SQLException {
    return new CatalogItemCategory(
        row.getObject("id", UUID.class),
        row.getObject("company_id", UUID.class),
        row.getString("code"),
        row.getString("name"),
        row.getBoolean("active"));
  }

  @Override
  public boolean activeUnit(String code) {
    return jdbc.sql(
            """
            SELECT EXISTS(SELECT 1 FROM catalog.units_of_measure WHERE code = :code AND active)
            """)
        .param("code", code)
        .query(Boolean.class)
        .single();
  }

  @Override
  public boolean currentTax(String code, LocalDate date) {
    return jdbc.sql(
            "SELECT EXISTS(SELECT 1 FROM catalog.tax_rate_versions r"
                + " WHERE classification_code = :code AND "
                + RATE_DATE
                + ")")
        .param("code", code)
        .param("date", date)
        .query(Boolean.class)
        .single();
  }

  @Override
  public List<Unit> units() {
    return jdbc.sql("SELECT * FROM catalog.units_of_measure ORDER BY code")
        .query((r, i) ->
            new Unit(
                r.getString("code"),
                r.getString("name"),
                r.getBoolean("active"),
                r.getBoolean("divisible")))
        .list();
  }

  @Override
  public List<Tax> taxes(LocalDate date) {
    return jdbc.sql(
            """
            SELECT t.code, t.name, r.rate, r.effective_from, r.effective_until
            FROM catalog.tax_classifications t LEFT JOIN catalog.tax_rate_versions r
                ON r.classification_code = t.code AND
            """
                + RATE_DATE
                + " ORDER BY t.code")
        .param("date", date)
        .query((r, i) ->
            new Tax(
                r.getString("code"),
                r.getString("name"),
                r.getBigDecimal("rate"),
                r.getObject("effective_from", LocalDate.class),
                r.getObject("effective_until", LocalDate.class)))
        .list();
  }

  @Override
  public Map<UUID, CatalogItemCommercialData> details(
      UUID companyId, List<UUID> ids, LocalDate date) {
    return jdbc
        .sql(
            """
            SELECT i.id, c.name AS category_name, s.supplier_id, r.rate, r.effective_from, r.effective_until
            FROM catalog.catalog_items i
            LEFT JOIN catalog.catalog_item_categories c ON c.company_id = i.company_id AND c.id = i.category_id
            LEFT JOIN catalog.supplier_items s ON s.company_id = i.company_id AND s.catalog_item_id = i.id
                AND s.is_primary AND s.valid_until IS NULL
            LEFT JOIN catalog.tax_rate_versions r ON r.classification_code = i.tax_classification_code AND
            """
                + RATE_DATE
                + " WHERE i.company_id = :company AND i.id IN (:ids)")
        .param("company", companyId)
        .param("ids", ids)
        .param("date", date)
        .query((r, i) ->
            Map.entry(
                r.getObject("id", UUID.class),
                new CatalogItemCommercialData(
                    r.getString("category_name"),
                    r.getObject("supplier_id", UUID.class),
                    r.getBigDecimal("rate"),
                    r.getObject("effective_from", LocalDate.class),
                    r.getObject("effective_until", LocalDate.class))))
        .list()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @Override
  public CatalogItemSummary summary(UUID companyId) {
    return jdbc.sql(
            """
            SELECT count(*), count(*) FILTER (WHERE type = 'PRODUCT'),
              count(*) FILTER (WHERE type = 'SERVICE'),
              count(*) FILTER (WHERE active), count(*) FILTER (WHERE NOT active)
            FROM catalog.catalog_items WHERE company_id = :company
            """)
        .param("company", companyId)
        .query((r, i) ->
            new CatalogItemSummary(
                r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4), r.getLong(5)))
        .single();
  }

  @Override
  public CatalogItemHistory history(UUID companyId, UUID itemId, int page, int size) {
    var filter =
        " FROM catalog.catalog_item_history WHERE company_id = :company AND catalog_item_id = :id";
    var total =
        jdbc.sql("SELECT count(*)" + filter)
            .param("company", companyId)
            .param("id", itemId)
            .query(Long.class)
            .single();
    var content =
        jdbc.sql("SELECT *" + filter + " ORDER BY id DESC LIMIT :size OFFSET :offset")
            .param("company", companyId)
            .param("id", itemId)
            .param("size", size)
            .param("offset", (long) page * size)
            .query((r, i) ->
                new CatalogItemHistory.Entry(
                    r.getLong("id"),
                    r.getObject("actor_id", UUID.class),
                    r.getString("operation"),
                    r.getTimestamp("occurred_at").toInstant(),
                    r.getString("previous_values"),
                    r.getString("new_values")))
            .list();
    return new CatalogItemHistory(content, page, size, total);
  }
}
