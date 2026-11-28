package com.sanigest.inventory.infrastructure.persistence.stock;

import com.sanigest.inventory.application.port.out.persistence.stock.SaleStockJournal;
import com.sanigest.inventory.domain.exception.InventoryViolation;
import com.sanigest.inventory.domain.model.stock.StockKey;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSaleStockJournal implements SaleStockJournal {
  private final JdbcTemplate jdbc;

  JdbcSaleStockJournal(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean claim(UUID company, UUID sale, String hash, boolean reverse) {
    int created =
        jdbc.update(
            """
            INSERT INTO inventory.sale_stock_effects (company_id, sale_id, request_hash, reversed)
            VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
            """,
            company,
            sale,
            hash,
            reverse);
    if (created == 1) {
      return !reverse;
    }
    var previous =
        jdbc.queryForObject(
            """
            SELECT request_hash, reversed FROM inventory.sale_stock_effects
            WHERE company_id = ? AND sale_id = ? FOR UPDATE
            """,
            (row, index) -> new Effect(row.getString(1), row.getBoolean(2)),
            company,
            sale);
    if (reverse) {
      return !previous.reversed()
          && jdbc.update(
                  """
                  UPDATE inventory.sale_stock_effects SET reversed = true
                  WHERE company_id = ? AND sale_id = ?
                  """,
                  company,
                  sale)
              == 1;
    }
    if (previous.reversed() || !Objects.equals(previous.hash(), hash)) {
      throw InventoryViolation.conflict("SALE_STOCK_CONFLICT");
    }
    return false;
  }

  @Override
  public void lockWarehouses(UUID company) {
    jdbc.queryForList(
        """
        SELECT id FROM inventory.warehouses WHERE company_id = ? ORDER BY id FOR UPDATE
        """,
        UUID.class,
        company);
  }

  @Override
  public List<StockKey> tracked(UUID company, Collection<UUID> items) {
    return jdbc.query(
        """
        SELECT catalog_item_id, warehouse_id FROM inventory.stock_positions
        WHERE company_id = ? AND catalog_item_id = ANY(CAST(? AS uuid[])) AND tracking_enabled
        """,
        (row, index) ->
            new StockKey(company, row.getObject(1, UUID.class), row.getObject(2, UUID.class)),
        company,
        "{" + String.join(",", items.stream().map(UUID::toString).toList()) + "}");
  }

  @Override
  public List<Issue> issues(UUID company, UUID sale) {
    return jdbc.query(
        """
        SELECT catalog_item_id, warehouse_id, quantity FROM inventory.stock_movements
        WHERE company_id = ? AND origin_type = 'SALE' AND origin_id = ? AND movement_type = 'SALE_ISSUE'
        ORDER BY warehouse_id, catalog_item_id
        """,
        (row, index) ->
            new Issue(
                new StockKey(company, row.getObject(1, UUID.class), row.getObject(2, UUID.class)),
                row.getBigDecimal(3)),
        company,
        sale);
  }

  private record Effect(String hash, boolean reversed) {}
}
