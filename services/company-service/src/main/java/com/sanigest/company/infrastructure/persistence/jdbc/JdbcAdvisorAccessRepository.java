package com.sanigest.company.infrastructure.persistence.jdbc;

import com.sanigest.company.application.port.out.persistence.AdvisorAccessRepository;
import com.sanigest.company.application.port.out.persistence.AuthorizedAdvisorCompany;
import com.sanigest.company.domain.model.AdvisorAccess;
import com.sanigest.company.domain.model.AdvisorPermission;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAdvisorAccessRepository implements AdvisorAccessRepository {
  private static final String SELECT_ACCESS =
      """
      SELECT a.advisor_id, a.revision, p.module, p.scope
      FROM company_advisor_access a
      LEFT JOIN company_advisor_permissions p
        ON p.company_id = a.company_id AND p.advisor_id = a.advisor_id AND p.revoked_at IS NULL
      WHERE a.company_id = ?
      """;
  private static final String ORDER = " ORDER BY a.advisor_id, p.module, p.scope";
  private final JdbcTemplate jdbc;

  JdbcAdvisorAccessRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<AdvisorAccess> list(UUID companyId) {
    return load(SELECT_ACCESS + ORDER, companyId);
  }

  @Override
  public List<AuthorizedAdvisorCompany> listAuthorizedCompanies(UUID advisorId) {
    return jdbc.query(
        """
        SELECT c.id AS company_id, c.name AS company_name, p.module, p.scope
        FROM company_advisor_access a
        JOIN companies c ON c.id = a.company_id
        JOIN company_advisor_permissions p
          ON p.company_id = a.company_id AND p.advisor_id = a.advisor_id
        WHERE a.advisor_id = ? AND c.status = 'ACTIVE' AND p.revoked_at IS NULL
        ORDER BY c.name, c.id, p.module, p.scope
        """,
        rows -> {
          var companies = new LinkedHashMap<UUID, CompanyPermissions>();
          while (rows.next()) {
            UUID companyId = rows.getObject("company_id", UUID.class);
            String companyName = rows.getString("company_name");
            var company =
                companies.computeIfAbsent(
                    companyId, id -> new CompanyPermissions(companyName, new ArrayList<>()));
            company
                .permissions()
                .add(
                    new AdvisorPermission(
                        AdvisorPermission.Module.valueOf(rows.getString("module")),
                        AdvisorPermission.Scope.valueOf(rows.getString("scope"))));
          }
          return companies.entrySet().stream()
              .map(entry ->
                  new AuthorizedAdvisorCompany(
                      entry.getKey(), entry.getValue().name(), entry.getValue().permissions()))
              .toList();
        },
        advisorId);
  }

  @Override
  public AdvisorAccess find(UUID companyId, UUID advisorId) {
    return load(SELECT_ACCESS + " AND a.advisor_id = ?" + ORDER, companyId, advisorId).stream()
        .findFirst()
        .orElseGet(() -> new AdvisorAccess(advisorId, 0, List.of()));
  }

  private List<AdvisorAccess> load(String sql, UUID... parameters) {
    return jdbc.query(
        sql,
        rows -> {
          var advisors = new LinkedHashMap<UUID, StoredAccess>();
          while (rows.next()) {
            UUID advisor = rows.getObject("advisor_id", UUID.class);
            long revision = rows.getLong("revision");
            var stored =
                advisors.computeIfAbsent(
                    advisor, id -> new StoredAccess(revision, new ArrayList<>()));
            String module = rows.getString("module");
            if (module != null) {
              stored
                  .permissions()
                  .add(
                      new AdvisorPermission(
                          AdvisorPermission.Module.valueOf(module),
                          AdvisorPermission.Scope.valueOf(rows.getString("scope"))));
            }
          }
          return advisors.entrySet().stream()
              .map(entry ->
                  new AdvisorAccess(
                      entry.getKey(), entry.getValue().revision(), entry.getValue().permissions()))
              .toList();
        },
        (Object[]) parameters);
  }

  @Override
  public boolean allows(UUID companyId, UUID advisorId, AdvisorPermission permission) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM company_advisor_permissions p JOIN companies c ON c.id = p.company_id
              WHERE p.company_id = ? AND p.advisor_id = ? AND p.module = ? AND p.scope = ?
                AND p.revoked_at IS NULL AND c.status = 'ACTIVE'
            )
            """,
            Boolean.class,
            companyId,
            advisorId,
            permission.module().name(),
            permission.scope().name()));
  }

  @Override
  public void saveRevision(UUID companyId, UUID advisorId, long previousRevision) {
    if (previousRevision == 0) {
      jdbc.update(
          "INSERT INTO company_advisor_access (company_id, advisor_id, revision) VALUES (?, ?, 1)",
          companyId,
          advisorId);
    } else {
      jdbc.update(
          "UPDATE company_advisor_access SET revision = revision + 1 WHERE company_id = ? AND advisor_id = ?",
          companyId,
          advisorId);
    }
  }

  @Override
  public void grant(UUID companyId, UUID advisorId, AdvisorPermission permission, Instant at) {
    int updated =
        jdbc.update(
            """
            UPDATE company_advisor_permissions SET granted_at = ?, revoked_at = NULL
            WHERE company_id = ? AND advisor_id = ? AND module = ? AND scope = ?
            """,
            at.atOffset(ZoneOffset.UTC),
            companyId,
            advisorId,
            permission.module().name(),
            permission.scope().name());
    if (updated == 0) {
      jdbc.update(
          """
          INSERT INTO company_advisor_permissions (company_id, advisor_id, module, scope, granted_at)
          VALUES (?, ?, ?, ?, ?)
          """,
          companyId,
          advisorId,
          permission.module().name(),
          permission.scope().name(),
          at.atOffset(ZoneOffset.UTC));
    }
  }

  @Override
  public void revoke(UUID companyId, UUID advisorId, AdvisorPermission permission, Instant at) {
    jdbc.update(
        """
        UPDATE company_advisor_permissions SET revoked_at = GREATEST(granted_at, ?)
        WHERE company_id = ? AND advisor_id = ? AND module = ? AND scope = ? AND revoked_at IS NULL
        """,
        at.atOffset(ZoneOffset.UTC),
        companyId,
        advisorId,
        permission.module().name(),
        permission.scope().name());
  }

  private record CompanyPermissions(String name, List<AdvisorPermission> permissions) {}

  private record StoredAccess(long revision, List<AdvisorPermission> permissions) {}
}
