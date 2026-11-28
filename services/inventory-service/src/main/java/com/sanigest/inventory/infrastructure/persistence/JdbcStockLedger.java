package com.sanigest.inventory.infrastructure.persistence;

import static com.sanigest.inventory.infrastructure.persistence.InventorySql.instant;
import static com.sanigest.inventory.infrastructure.persistence.InventorySql.timestamp;
import static com.sanigest.inventory.infrastructure.persistence.InventorySql.uuid;

import com.sanigest.inventory.application.dto.InventoryQueries;
import com.sanigest.inventory.application.dto.InventoryQueries.StockEntry;
import com.sanigest.inventory.application.port.out.persistence.StockLedger;
import com.sanigest.inventory.domain.InventoryViolation;
import com.sanigest.inventory.domain.StockBalance;
import com.sanigest.inventory.domain.StockConfiguration;
import com.sanigest.inventory.domain.StockKey;
import com.sanigest.inventory.domain.StockMovement;
import com.sanigest.inventory.domain.StockPosition;
import com.sanigest.security.RequestCorrelation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcStockLedger implements StockLedger {
  private static final String KEY =
      "company_id=:company AND catalog_item_id=:item AND warehouse_id=:warehouse";
  private static final String AVAILABILITY =
      """
      CASE WHEN NOT tracking_enabled THEN 'NOT_TRACKED'
           WHEN on_hand = reserved THEN 'OUT'
           WHEN on_hand - reserved <= minimum_stock THEN 'LOW'
           ELSE 'AVAILABLE' END
      """;
  private final JdbcClient db;

  JdbcStockLedger(JdbcClient db) {
    this.db = db;
  }

  @Override
  public Optional<StockPosition> find(StockKey key) {
    return bind("SELECT * FROM inventory.stock_positions WHERE " + KEY, key)
        .query(JdbcStockLedger::position)
        .optional();
  }

  @Override
  public void save(StockPosition value) {
    var configuration = value.configuration();
    bind(
            """
            INSERT INTO inventory.stock_positions (company_id,catalog_item_id,warehouse_id,
                tracking_enabled,initialized,minimum_stock,maximum_stock,location,on_hand,reserved,
                unit_code,divisible,sku,item_name,catalog_verified_at,created_at,updated_at,revision,movement_sequence)
            VALUES (:company,:item,:warehouse,:tracking,:initialized,:minimum,:maximum,:location,:physical,:reserved,
                :unit,:divisible,:sku,:name,:verified,:created,:updated,:revision,:sequence)
            ON CONFLICT (company_id,catalog_item_id,warehouse_id) DO UPDATE SET
                tracking_enabled=excluded.tracking_enabled, initialized=excluded.initialized,
                minimum_stock=excluded.minimum_stock, maximum_stock=excluded.maximum_stock,
                location=excluded.location, on_hand=excluded.on_hand, reserved=excluded.reserved,
                unit_code=excluded.unit_code, divisible=excluded.divisible, sku=excluded.sku,
                item_name=excluded.item_name, catalog_verified_at=excluded.catalog_verified_at,
                updated_at=excluded.updated_at, revision=excluded.revision, movement_sequence=excluded.movement_sequence
            """,
            value.key())
        .param("tracking", configuration.trackingEnabled())
        .param("initialized", value.initialized())
        .param("minimum", configuration.minimumStock())
        .param("maximum", configuration.maximumStock())
        .param("location", configuration.location())
        .param("physical", value.balance().onHand())
        .param("reserved", value.balance().reserved())
        .param("unit", value.unitCode())
        .param("divisible", value.divisible())
        .param("sku", value.sku())
        .param("name", value.itemName())
        .param("verified", timestamp(value.catalogVerifiedAt()))
        .param("created", timestamp(value.createdAt()))
        .param("updated", timestamp(value.updatedAt()))
        .param("revision", value.revision())
        .param("sequence", value.movementSequence())
        .update();
  }

  @Override
  public void append(StockMovement value) {
    bind(
            """
            INSERT INTO inventory.stock_movements (company_id,catalog_item_id,warehouse_id,id,
                movement_type,direction,quantity,unit_code,origin_type,origin_id,effect_id,actor_id,reason,
                occurred_at,recorded_at,sequence,balance_after,correlation_id)
            VALUES (:company,:item,:warehouse,:id,:type,:direction,:quantity,:unit,:originType,:origin,
                :effect,:actor,:reason,:occurred,:recorded,:sequence,:balance,:trace)
            """,
            value.key())
        .param("id", value.id())
        .param("type", value.movementType().name())
        .param("direction", value.direction().name())
        .param("quantity", value.quantity())
        .param("unit", value.unitCode())
        .param("originType", value.originType().name())
        .param("origin", value.originId())
        .param("effect", value.effectId())
        .param("actor", value.actorId())
        .param("reason", value.reason())
        .param("occurred", timestamp(value.occurredAt()))
        .param("recorded", timestamp(value.recordedAt()))
        .param("sequence", value.sequence())
        .param("balance", value.balanceAfter())
        .param("trace", RequestCorrelation.current())
        .update();
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public InventoryQueries.Page<StockEntry> list(UUID company, InventoryQueries.Stock query) {
    var sql = byPosition(company, query.warehouseId(), query.catalogItemId());
    if (!query.catalogItemIds().isEmpty()) {
      sql.filter("catalog_item_id IN (:items)", "items", query.catalogItemIds());
    }
    sql.filter("(" + AVAILABILITY + ") = :state", "state", query.availability());
    sql.filter("tracking_enabled=:tracking", "tracking", query.trackingEnabled());
    sql.search(
        "(strpos(lower(sku),:search)>0 OR strpos(lower(item_name),:search)>0)", query.search());
    var column =
        switch (query.paging().sort()) {
          case UPDATED_AT -> "updated_at";
          case AVAILABLE -> "(on_hand - reserved)";
          case NAME -> "item_name";
          default -> throw InventoryViolation.invalid("INVALID_SORT");
        };
    return sql.page(
        db,
        """
        (SELECT p.*, w.name AS warehouse_name
         FROM inventory.stock_positions p JOIN inventory.warehouses w
           ON w.company_id = p.company_id AND w.id = p.warehouse_id) positions
        """,
        column + " " + query.paging().order() + ", warehouse_id, catalog_item_id",
        query.paging(),
        (row, index) -> new StockEntry(position(row, index), row.getString("warehouse_name")));
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public InventoryQueries.Page<StockMovement> movements(
      UUID company, InventoryQueries.Movements query) {
    var sql = byPosition(company, query.warehouseId(), query.catalogItemId());
    sql.filter("movement_type=:type", "type", query.movementType());
    sql.filter("direction=:direction", "direction", query.direction());
    sql.filter("origin_type=:originType", "originType", query.originType());
    sql.filter("origin_id=:origin", "origin", query.originId());
    sql.filter("occurred_at >= :start", "start", timestamp(query.dateFrom()));
    sql.filter("occurred_at < :end", "end", timestamp(query.dateTo()));
    var column =
        switch (query.paging().sort()) {
          case OCCURRED_AT -> "occurred_at";
          case QUANTITY -> "quantity";
          default -> throw InventoryViolation.invalid("INVALID_SORT");
        };
    var order = query.paging().order();
    return sql.page(
        db,
        "inventory.stock_movements",
        column + " " + order + ", sequence " + order + ", id",
        query.paging(),
        JdbcStockLedger::movement);
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public InventoryQueries.Summary summary(UUID company, UUID warehouse) {
    var warehouseSql = new InventorySql(company);
    warehouseSql.filter("id=:warehouse", "warehouse", warehouse);
    long activeWarehouses =
        warehouseSql
            .query(
                db,
                "SELECT count(*) FROM (SELECT * FROM inventory.warehouses WHERE status='ACTIVE') w")
            .query(Long.class)
            .single();
    return byPosition(company, warehouse, null)
        .query(
            db,
            """
            SELECT count(*) FILTER (WHERE tracking_enabled) AS tracked,
                   count(*) FILTER (WHERE tracking_enabled AND on_hand - reserved > minimum_stock) AS available,
                   count(*) FILTER (WHERE tracking_enabled AND on_hand > reserved
                       AND on_hand - reserved <= minimum_stock) AS low,
                   count(*) FILTER (WHERE tracking_enabled AND on_hand = reserved) AS out,
                   count(*) FILTER (WHERE NOT tracking_enabled) AS untracked
            FROM inventory.stock_positions
            """)
        .query((row, index) ->
            new InventoryQueries.Summary(
                activeWarehouses,
                row.getLong("tracked"),
                row.getLong("available"),
                row.getLong("low"),
                row.getLong("out"),
                row.getLong("untracked")))
        .single();
  }

  @Override
  public Optional<Reconciliation> reconcile(StockKey key) {
    return bind(
            """
            SELECT on_hand, COALESCE((SELECT sum(CASE direction WHEN 'IN' THEN quantity ELSE -quantity END)
                FROM inventory.stock_movements WHERE
            """
                + KEY
                + "), 0) AS reconstructed FROM inventory.stock_positions WHERE "
                + KEY,
            key)
        .query((row, index) ->
            new Reconciliation(row.getBigDecimal("on_hand"), row.getBigDecimal("reconstructed")))
        .optional();
  }

  private static InventorySql byPosition(UUID company, UUID warehouse, UUID item) {
    var sql = new InventorySql(company);
    sql.filter("warehouse_id=:warehouse", "warehouse", warehouse);
    sql.filter("catalog_item_id=:item", "item", item);
    return sql;
  }

  private JdbcClient.StatementSpec bind(String sql, StockKey key) {
    return db.sql(sql)
        .param("company", key.companyId())
        .param("item", key.catalogItemId())
        .param("warehouse", key.warehouseId());
  }

  private static StockKey key(ResultSet row) throws SQLException {
    return new StockKey(
        uuid(row, "company_id"), uuid(row, "catalog_item_id"), uuid(row, "warehouse_id"));
  }

  private static StockPosition position(ResultSet row, int index) throws SQLException {
    return new StockPosition(
        key(row),
        new StockConfiguration(
            row.getBoolean("tracking_enabled"),
            row.getBigDecimal("minimum_stock"),
            row.getBigDecimal("maximum_stock"),
            row.getString("location")),
        new StockBalance(row.getBigDecimal("on_hand"), row.getBigDecimal("reserved")),
        row.getBoolean("initialized"),
        row.getString("unit_code"),
        row.getBoolean("divisible"),
        row.getString("sku"),
        row.getString("item_name"),
        instant(row, "catalog_verified_at"),
        instant(row, "created_at"),
        instant(row, "updated_at"),
        row.getLong("revision"),
        row.getLong("movement_sequence"));
  }

  private static StockMovement movement(ResultSet row, int index) throws SQLException {
    return new StockMovement(
        uuid(row, "id"),
        key(row),
        StockMovement.Type.valueOf(row.getString("movement_type")),
        StockMovement.Direction.valueOf(row.getString("direction")),
        row.getBigDecimal("quantity"),
        row.getString("unit_code"),
        StockMovement.Origin.valueOf(row.getString("origin_type")),
        uuid(row, "origin_id"),
        uuid(row, "effect_id"),
        uuid(row, "actor_id"),
        row.getString("reason"),
        instant(row, "occurred_at"),
        instant(row, "recorded_at"),
        row.getLong("sequence"),
        row.getBigDecimal("balance_after"));
  }
}
