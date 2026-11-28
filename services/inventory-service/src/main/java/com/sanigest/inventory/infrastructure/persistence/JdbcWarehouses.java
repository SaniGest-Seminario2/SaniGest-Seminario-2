package com.sanigest.inventory.infrastructure.persistence;

import static com.sanigest.inventory.infrastructure.persistence.InventorySql.instant;
import static com.sanigest.inventory.infrastructure.persistence.InventorySql.timestamp;
import static com.sanigest.inventory.infrastructure.persistence.InventorySql.uuid;

import com.sanigest.inventory.application.dto.InventoryQueries;
import com.sanigest.inventory.application.port.out.persistence.Warehouses;
import com.sanigest.inventory.domain.exception.InventoryViolation;
import com.sanigest.inventory.domain.model.Warehouse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcWarehouses implements Warehouses {
  private final JdbcClient db;

  JdbcWarehouses(JdbcClient db) {
    this.db = db;
  }

  @Override
  public Optional<Warehouse> find(UUID company, UUID id, boolean lock) {
    return db.sql(
            "SELECT * FROM inventory.warehouses WHERE company_id=:company AND id=:id"
                + (lock ? " FOR UPDATE" : ""))
        .param("company", company)
        .param("id", id)
        .query(JdbcWarehouses::map)
        .optional();
  }

  @Override
  public boolean insert(Warehouse warehouse) {
    return bind(
                """
                INSERT INTO inventory.warehouses
                (company_id,id,code,name,location,status,created_at,updated_at,revision)
                VALUES (:company,:id,:code,:name,:location,:status,:created,:updated,:revision)
                ON CONFLICT (company_id,code) DO NOTHING
                """,
                warehouse)
            .update()
        == 1;
  }

  @Override
  public void update(Warehouse warehouse) {
    try {
      bind(
              """
              UPDATE inventory.warehouses SET code=:code,name=:name,location=:location,
                  status=:status,updated_at=:updated,revision=:revision
              WHERE company_id=:company AND id=:id
              """,
              warehouse)
          .update();
    } catch (DuplicateKeyException conflict) {
      throw InventoryViolation.conflict("WAREHOUSE_CODE_CONFLICT");
    }
  }

  @Override
  public boolean hasStock(UUID company, UUID warehouse) {
    return db.sql(
            """
            SELECT EXISTS (SELECT 1 FROM inventory.stock_positions WHERE company_id=:company
                AND warehouse_id=:id AND (on_hand > 0 OR reserved > 0))
            """)
        .param("company", company)
        .param("id", warehouse)
        .query(Boolean.class)
        .single();
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public InventoryQueries.Page<Warehouse> list(UUID company, InventoryQueries.Warehouses query) {
    var sql = new InventorySql(company);
    sql.filter("status=:status", "status", query.status());
    sql.search("(strpos(lower(code),:search)>0 OR strpos(lower(name),:search)>0)", query.search());
    var column =
        switch (query.paging().sort()) {
          case CODE -> "code";
          case NAME -> "name";
          case UPDATED_AT -> "updated_at";
          default -> throw InventoryViolation.invalid("INVALID_SORT");
        };
    return sql.page(
        db,
        "inventory.warehouses",
        column + " " + query.paging().order() + ", id",
        query.paging(),
        JdbcWarehouses::map);
  }

  private JdbcClient.StatementSpec bind(String sql, Warehouse value) {
    return db.sql(sql)
        .param("company", value.companyId())
        .param("id", value.id())
        .param("code", value.code())
        .param("name", value.name())
        .param("location", value.location())
        .param("status", value.status().name())
        .param("created", timestamp(value.createdAt()))
        .param("updated", timestamp(value.updatedAt()))
        .param("revision", value.revision());
  }

  private static Warehouse map(ResultSet row, int index) throws SQLException {
    return new Warehouse(
        uuid(row, "id"),
        uuid(row, "company_id"),
        row.getString("code"),
        row.getString("name"),
        row.getString("location"),
        Warehouse.Status.valueOf(row.getString("status")),
        instant(row, "created_at"),
        instant(row, "updated_at"),
        row.getLong("revision"));
  }
}
