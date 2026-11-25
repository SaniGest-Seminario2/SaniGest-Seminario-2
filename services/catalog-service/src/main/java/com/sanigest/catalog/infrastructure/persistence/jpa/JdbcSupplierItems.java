package com.sanigest.catalog.infrastructure.persistence.jpa;

import static com.sanigest.catalog.application.dto.SupplierItemQuery.Direction.SUPPLIED_ITEMS;
import static com.sanigest.catalog.application.exception.CatalogItemAccessException.Reason.CATALOG_ITEM_NOT_FOUND;
import static com.sanigest.catalog.application.exception.SupplierAccessException.Reason.SUPPLIER_NOT_FOUND;

import com.sanigest.catalog.application.dto.SupplierItemPage;
import com.sanigest.catalog.application.dto.SupplierItemPage.Entry;
import com.sanigest.catalog.application.dto.SupplierItemPage.Offer;
import com.sanigest.catalog.application.dto.SupplierItemPage.Party;
import com.sanigest.catalog.application.dto.SupplierItemQuery;
import com.sanigest.catalog.application.exception.CatalogItemAccessException;
import com.sanigest.catalog.application.exception.SupplierAccessException;
import com.sanigest.catalog.application.port.out.persistence.SupplierItemRepository;
import com.sanigest.catalog.domain.enums.CatalogItemType;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.enums.SupplierSupplyType;
import com.sanigest.catalog.domain.model.SupplierItemOffer;
import com.sanigest.catalog.domain.model.SupplierProfile;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSupplierItems implements SupplierItemRepository {
  private static final String LINKS =
      """
      FROM catalog.supplier_items link
      """;
  private static final String SUPPLIER_KEY = "company_id = :company AND supplier_id = :supplier";
  private static final String ITEM_KEY = "company_id = :company AND catalog_item_id = :item";
  private final JdbcClient jdbc;

  JdbcSupplierItems(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public LockedSupplier lockSupplier(UUID companyId, UUID supplierId) {
    return jdbc.sql(
            """
            SELECT status, supply_type FROM catalog.suppliers
            WHERE company_id = :company AND id = :supplier FOR NO KEY UPDATE
            """)
        .param("company", companyId)
        .param("supplier", supplierId)
        .query((row, index) -> {
          var type = row.getString("supply_type");
          return new LockedSupplier(
              SupplierStatus.valueOf(row.getString("status")),
              new SupplierProfile(
                  null,
                  type == null ? null : SupplierSupplyType.valueOf(type),
                  null,
                  null,
                  null,
                  null));
        })
        .optional()
        .orElseThrow(() -> new SupplierAccessException(SUPPLIER_NOT_FOUND));
  }

  @Override
  public Map<UUID, LockedItem> lockItems(UUID companyId, UUID supplierId, List<UUID> requestedIds) {
    return jdbc
        .sql(
            """
            SELECT id, type, active FROM catalog.catalog_items
            WHERE company_id = :company AND (id IN (:requested) OR id IN (
                SELECT catalog_item_id FROM catalog.supplier_items
                WHERE company_id = :company AND supplier_id = :supplier AND valid_until IS NULL))
            ORDER BY id FOR NO KEY UPDATE
            """)
        .param("company", companyId)
        .param("supplier", supplierId)
        .param(
            "requested",
            requestedIds.isEmpty() ? java.util.Collections.singletonList(null) : requestedIds)
        .query((row, index) ->
            Map.entry(
                row.getObject("id", UUID.class),
                new LockedItem(
                    CatalogItemType.valueOf(row.getString("type")), row.getBoolean("active"))))
        .list()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @Override
  public void retireOmitted(UUID companyId, UUID supplierId, List<UUID> requestedIds) {
    var sql =
        "UPDATE catalog.supplier_items SET valid_until = clock_timestamp(), is_primary = false WHERE "
            + SUPPLIER_KEY
            + " AND valid_until IS NULL";
    var statement =
        jdbc.sql(requestedIds.isEmpty() ? sql : sql + " AND catalog_item_id NOT IN (:requested)")
            .param("company", companyId)
            .param("supplier", supplierId);
    if (!requestedIds.isEmpty()) {
      statement.param("requested", requestedIds);
    }
    statement.update();
  }

  @Override
  public boolean hasOtherPrimary(UUID companyId, UUID supplierId, UUID itemId) {
    return otherPrimary(
            "SELECT EXISTS(SELECT 1 " + LINKS + " WHERE ", ")", companyId, supplierId, itemId)
        .query(Boolean.class)
        .single();
  }

  @Override
  public void clearOtherPrimary(UUID companyId, UUID supplierId, UUID itemId) {
    otherPrimary(
            "UPDATE catalog.supplier_items SET is_primary = false WHERE ",
            "",
            companyId,
            supplierId,
            itemId)
        .update();
  }

  private JdbcClient.StatementSpec otherPrimary(
      String prefix, String suffix, UUID companyId, UUID supplierId, UUID itemId) {
    return jdbc.sql(prefix + ITEM_KEY + " AND supplier_id <> :supplier AND is_primary" + suffix)
        .param("company", companyId)
        .param("supplier", supplierId)
        .param("item", itemId);
  }

  @Override
  public void apply(UUID companyId, UUID supplierId, SupplierItemOffer offer) {
    jdbc.sql(
            """
            INSERT INTO catalog.supplier_items AS link
                (company_id, supplier_id, catalog_item_id, offered_cost, supplier_reference, is_primary)
            VALUES (:company, :supplier, :item, :cost, :reference, :primary)
            ON CONFLICT (company_id, supplier_id, catalog_item_id) DO UPDATE SET
                offered_cost = excluded.offered_cost, supplier_reference = excluded.supplier_reference,
                is_primary = excluded.is_primary, valid_until = NULL,
                valid_from = CASE WHEN link.valid_until IS NULL THEN link.valid_from ELSE excluded.valid_from END
            WHERE link.valid_until IS NOT NULL OR
                (link.offered_cost, link.supplier_reference, link.is_primary)
                IS DISTINCT FROM (excluded.offered_cost, excluded.supplier_reference, excluded.is_primary)
            """)
        .param("company", companyId)
        .param("supplier", supplierId)
        .param("item", offer.catalogItemId())
        .param("cost", offer.offeredCost())
        .param("reference", offer.supplierReference())
        .param("primary", offer.primary())
        .update();
  }

  @Override
  public SupplierItemPage search(SupplierItemQuery query) {
    boolean forward = query.direction() == SUPPLIED_ITEMS;
    requireOwner(query, forward);
    var filter =
        LINKS
            + (forward
                ? """
                JOIN catalog.catalog_items party
                  ON party.company_id = link.company_id AND party.id = link.catalog_item_id
                WHERE link.company_id = :company AND link.supplier_id = :owner
                """
                : """
                JOIN catalog.suppliers party
                  ON party.company_id = link.company_id AND party.id = link.supplier_id
                WHERE link.company_id = :company AND link.catalog_item_id = :owner
                """)
            + """
            AND (:status = '' OR (:status = 'ACTIVE') = (link.valid_until IS NULL))
            AND (:pattern = '' OR party.name ILIKE :pattern ESCAPE '!'
              OR link.supplier_reference ILIKE :pattern ESCAPE '!')
            """;
    long total = filtered("SELECT count(*) " + filter, query).query(Long.class).single();
    long offset = (long) query.page() * query.size();
    if (offset >= total) {
      return new SupplierItemPage(List.of(), query.page(), query.size(), total);
    }
    var fields =
        forward
            ? "party.type AS type, party.active"
            : "NULL AS type, party.status = 'ACTIVE' AS active";
    var content =
        filtered(
                """
                SELECT party.id, party.name, link.offered_cost, link.supplier_reference,
                    link.is_primary, link.valid_from, link.valid_until,
                """
                    + fields
                    + " "
                    + filter
                    + " ORDER BY party.name, party.id LIMIT :size OFFSET :offset",
                query)
            .param("size", query.size())
            .param("offset", offset)
            .query((row, index) -> {
              var until = row.getTimestamp("valid_until");
              return new Entry(
                  new Party(
                      row.getObject("id", UUID.class),
                      row.getString("name"),
                      row.getString("type"),
                      row.getBoolean("active")),
                  new Offer(
                      row.getBigDecimal("offered_cost"),
                      row.getString("supplier_reference"),
                      row.getBoolean("is_primary"),
                      row.getTimestamp("valid_from").toInstant(),
                      until == null ? null : until.toInstant()));
            })
            .list();
    return new SupplierItemPage(content, query.page(), query.size(), total);
  }

  private void requireOwner(SupplierItemQuery query, boolean forward) {
    var table = forward ? "catalog.suppliers" : "catalog.catalog_items";
    boolean exists =
        jdbc.sql(
                "SELECT EXISTS(SELECT 1 FROM "
                    + table
                    + " WHERE company_id = :company AND id = :id)")
            .param("company", query.companyId())
            .param("id", query.ownerId())
            .query(Boolean.class)
            .single();
    if (!exists) {
      if (forward) {
        throw new SupplierAccessException(SUPPLIER_NOT_FOUND);
      }
      throw new CatalogItemAccessException(CATALOG_ITEM_NOT_FOUND);
    }
  }

  private JdbcClient.StatementSpec filtered(String sql, SupplierItemQuery query) {
    return jdbc.sql(sql)
        .param("company", query.companyId())
        .param("owner", query.ownerId())
        .param("status", query.status() == null ? "" : query.status().name())
        .param("pattern", LikePattern.literalContains(query.search()));
  }
}
