package com.sanigest.company.infrastructure.persistence.jdbc;

import com.sanigest.company.application.port.out.persistence.CompanyAuditRepository;
import com.sanigest.company.domain.enums.AuditOperation;
import com.sanigest.company.domain.enums.AuditResult;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import com.sanigest.company.domain.model.AdvisorPermission;
import com.sanigest.company.domain.model.CompanyAuditEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCompanyAuditRepository implements CompanyAuditRepository {
  private final JdbcTemplate jdbc;

  JdbcCompanyAuditRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void append(CompanyAuditEvent event) {
    jdbc.update(
        """
        INSERT INTO company_audit_log (
          id, company_id, actor_id, target_user_id, operation, occurred_at, result,
          previous_role, new_role, previous_status, new_status, advisor_module, advisor_scope, correlation_id
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        event.id(),
        event.companyId(),
        event.actorId(),
        event.targetUserId(),
        event.operation().name(),
        event.occurredAt().atOffset(ZoneOffset.UTC),
        event.result().name(),
        enumName(event.previousRole()),
        enumName(event.newRole()),
        enumName(event.previousStatus()),
        enumName(event.newStatus()),
        event.advisorPermission() == null ? null : event.advisorPermission().module().name(),
        event.advisorPermission() == null ? null : event.advisorPermission().scope().name(),
        event.correlationId());
  }

  @Override
  public List<CompanyAuditEvent> findPage(
      UUID companyId, Instant from, Instant to, long offset, int limit) {
    var sql = new StringBuilder("SELECT * FROM company_audit_log WHERE company_id = ?");
    var parameters = new ArrayList<>();
    parameters.add(companyId);
    if (from != null) {
      sql.append(" AND occurred_at >= ?");
      parameters.add(from.atOffset(ZoneOffset.UTC));
    }
    if (to != null) {
      sql.append(" AND occurred_at <= ?");
      parameters.add(to.atOffset(ZoneOffset.UTC));
    }
    sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?");
    parameters.add(limit);
    parameters.add(offset);
    return jdbc.query(sql.toString(), JdbcCompanyAuditRepository::event, parameters.toArray());
  }

  private static CompanyAuditEvent event(ResultSet row, int index) throws SQLException {
    return new CompanyAuditEvent(
        row.getObject("id", UUID.class),
        row.getObject("company_id", UUID.class),
        row.getObject("actor_id", UUID.class),
        row.getObject("target_user_id", UUID.class),
        AuditOperation.valueOf(row.getString("operation")),
        row.getObject("occurred_at", OffsetDateTime.class).toInstant(),
        AuditResult.valueOf(row.getString("result")),
        readEnum(row, "previous_role", CompanyRole.class),
        readEnum(row, "new_role", CompanyRole.class),
        readEnum(row, "previous_status", MembershipStatus.class),
        readEnum(row, "new_status", MembershipStatus.class),
        advisorPermission(row),
        row.getString("correlation_id"));
  }

  private static AdvisorPermission advisorPermission(ResultSet row) throws SQLException {
    var module = readEnum(row, "advisor_module", AdvisorPermission.Module.class);
    return module == null
        ? null
        : new AdvisorPermission(
            module, readEnum(row, "advisor_scope", AdvisorPermission.Scope.class));
  }

  private static String enumName(Enum<?> value) {
    return value == null ? null : value.name();
  }

  private static <E extends Enum<E>> E readEnum(ResultSet row, String column, Class<E> type)
      throws SQLException {
    String value = row.getString(column);
    return value == null ? null : Enum.valueOf(type, value);
  }
}
