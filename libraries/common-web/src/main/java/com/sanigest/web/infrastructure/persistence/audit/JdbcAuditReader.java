package com.sanigest.web.infrastructure.persistence.audit;

import com.sanigest.web.application.dto.audit.AuditPage;
import com.sanigest.web.application.dto.audit.AuditPage.Entry;
import com.sanigest.web.application.dto.audit.AuditPage.Filters;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcAuditReader {
  private final JdbcTemplate jdbc;
  private final String view;

  public JdbcAuditReader(JdbcTemplate jdbc, String view) {
    this.jdbc = jdbc;
    this.view = view;
  }

  public AuditPage read(UUID companyId, Filters filters) {
    var sql = new StringBuilder("SELECT * FROM " + view + " WHERE company_id = ?");
    var parameters = new ArrayList<Object>();
    parameters.add(companyId);
    if (filters.from() != null) {
      filter(sql, parameters, "occurred_at >= ?", filters.from().atOffset(ZoneOffset.UTC));
    }
    if (filters.to() != null) {
      filter(sql, parameters, "occurred_at <= ?", filters.to().atOffset(ZoneOffset.UTC));
    }
    filter(sql, parameters, "actor_id = ?", filters.actorId());
    filter(sql, parameters, "operation = ?", filters.operation());
    filter(sql, parameters, "resource_type = ?", filters.resourceType());
    filter(sql, parameters, "resource_id = ?", filters.resourceId());
    sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?");
    parameters.add(filters.size() + 1);
    parameters.add(filters.offset());
    var rows =
        jdbc.query(
            sql.toString(),
            (row, index) ->
                new Entry(
                    row.getObject("id", UUID.class),
                    row.getObject("actor_id", UUID.class),
                    row.getString("operation"),
                    row.getString("resource_type"),
                    row.getObject("resource_id", UUID.class),
                    row.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                    row.getString("result"),
                    row.getString("previous_state"),
                    row.getString("state"),
                    row.getString("correlation_id")),
            parameters.toArray());
    return new AuditPage(
        List.copyOf(rows.subList(0, Math.min(rows.size(), filters.size()))),
        filters.page(),
        filters.size(),
        rows.size() > filters.size());
  }

  private static void filter(
      StringBuilder sql, List<Object> parameters, String condition, Object value) {
    if (value != null) {
      sql.append(" AND ").append(condition);
      parameters.add(value);
    }
  }
}
