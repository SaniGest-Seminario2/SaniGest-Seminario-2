package com.sanigest.company.infrastructure.persistence.jdbc.audit;

import com.sanigest.company.application.port.out.persistence.audit.CompanyAuditRepository;
import com.sanigest.company.domain.enums.audit.AuditOperation;
import com.sanigest.company.domain.enums.audit.AuditResult;
import com.sanigest.company.domain.enums.team.CompanyRole;
import com.sanigest.company.domain.enums.team.MembershipStatus;
import com.sanigest.company.domain.model.advisor.AdvisorPermission;
import com.sanigest.company.domain.model.audit.CompanyAuditEvent;
import com.sanigest.web.application.dto.audit.AuditPage.Filters;
import java.sql.ResultSet;
import java.sql.SQLException;
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
          previous_role, new_role, previous_status, new_status, advisor_module,
          advisor_scope, correlation_id, invitation_id
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
        event.correlationId(),
        event.invitationId());
  }

  @Override
  public List<CompanyAuditEvent> findPage(UUID companyId, Filters filters) {
    var sql = new StringBuilder("SELECT * FROM company_audit_log WHERE company_id = ?");
    var parameters = new ArrayList<>();
    parameters.add(companyId);
    if (filters.from() != null) {
      sql.append(" AND occurred_at >= ?");
      parameters.add(filters.from().atOffset(ZoneOffset.UTC));
    }
    if (filters.to() != null) {
      sql.append(" AND occurred_at <= ?");
      parameters.add(filters.to().atOffset(ZoneOffset.UTC));
    }
    if (filters.actorId() != null) {
      sql.append(" AND actor_id = ?");
      parameters.add(filters.actorId());
    }
    if (filters.operation() != null) {
      sql.append(" AND operation = ?");
      parameters.add(filters.operation());
    }
    if (filters.resourceType() != null) {
      var operations =
          java.util.Arrays.stream(AuditOperation.values())
              .filter(operation -> operation.resourceType().equals(filters.resourceType()))
              .map(AuditOperation::name)
              .toList();
      if (operations.isEmpty()) {
        return List.of();
      }
      sql.append(" AND operation IN (")
          .append(String.join(", ", java.util.Collections.nCopies(operations.size(), "?")))
          .append(")");
      parameters.addAll(operations);
    }
    if (filters.resourceId() != null) {
      sql.append(" AND COALESCE(invitation_id, target_user_id) = ?");
      parameters.add(filters.resourceId());
    }
    sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?");
    parameters.add(filters.size() + 1);
    parameters.add(filters.offset());
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
        row.getString("correlation_id"),
        row.getObject("invitation_id", UUID.class));
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
