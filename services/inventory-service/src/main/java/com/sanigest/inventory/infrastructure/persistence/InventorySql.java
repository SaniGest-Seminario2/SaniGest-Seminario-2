package com.sanigest.inventory.infrastructure.persistence;

import com.sanigest.inventory.application.dto.InventoryQueries.Page;
import com.sanigest.inventory.application.dto.InventoryQueries.Paging;
import com.sanigest.inventory.domain.exception.InventoryViolation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

final class InventorySql {
  private final Map<String, Object> parameters = new LinkedHashMap<>();
  private final StringBuilder filter = new StringBuilder(" WHERE company_id = :company");

  InventorySql(UUID company) {
    parameters.put("company", company);
  }

  void filter(String condition, String parameter, Object value) {
    if (value != null) {
      filter.append(" AND ").append(condition);
      parameters.put(parameter, value instanceof Enum<?> e ? e.name() : value);
    }
  }

  void search(String condition, String search) {
    if (search != null && !search.isBlank()) {
      if (search.length() > 120 || search.indexOf(0) >= 0) {
        throw InventoryViolation.invalid("INVALID_SEARCH");
      }
      filter(condition, "search", search.strip().toLowerCase(Locale.ROOT));
    }
  }

  <T> Page<T> page(JdbcClient db, String source, String order, Paging paging, RowMapper<T> mapper) {
    long total = query(db, "SELECT count(*) FROM " + source).query(Long.class).single();
    var items =
        db.sql(
                "SELECT * FROM "
                    + source
                    + filter
                    + " ORDER BY "
                    + order
                    + " LIMIT :limit OFFSET :offset")
            .params(parameters)
            .param("limit", paging.size())
            .param("offset", paging.offset())
            .query(mapper)
            .list();
    return new Page<>(items, total, paging.page(), paging.size());
  }

  JdbcClient.StatementSpec query(JdbcClient db, String sql) {
    return db.sql(sql + filter).params(parameters);
  }

  static OffsetDateTime timestamp(Instant time) {
    return time == null ? null : time.atOffset(ZoneOffset.UTC);
  }

  static Instant instant(ResultSet row, String field) throws SQLException {
    return row.getObject(field, OffsetDateTime.class).toInstant();
  }

  static UUID uuid(ResultSet row, String field) throws SQLException {
    return row.getObject(field, UUID.class);
  }
}
