package com.sanigest.company.infrastructure.persistence.jdbc;

import com.sanigest.company.application.dto.AuthorizedCompany;
import com.sanigest.company.application.dto.CompanyMembership;
import com.sanigest.company.application.dto.CompanyProfile;
import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.CompanyConflictException;
import com.sanigest.company.application.exception.CompanySelectionRequiredException;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import com.sanigest.company.domain.model.Membership;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMembershipRepository implements MembershipRepository {
  private final JdbcTemplate jdbc;

  JdbcMembershipRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<ActiveAccess> findActiveAccess(UUID userId, UUID companyId, String permission) {
    var matches =
        jdbc.query(
            """
            SELECT m.company_id, c.name AS company_name, m.role, EXISTS (
              SELECT 1 FROM membership_permissions mp
              JOIN company_permissions cp ON cp.id = mp.permission_id
              WHERE mp.membership_id = m.id AND cp.company_id = m.company_id AND cp.permission = ?
            ) AS explicit_grant
            FROM company_memberships m JOIN companies c ON c.id = m.company_id
            WHERE m.user_id = ? AND m.company_id = ? AND m.status = 'ACTIVE' AND c.status = 'ACTIVE'
            """,
            (row, index) ->
                new ActiveAccess(
                    row.getObject("company_id", UUID.class),
                    CompanyRole.valueOf(row.getString("role")),
                    row.getBoolean("explicit_grant"),
                    row.getString("company_name")),
            permission,
            userId,
            companyId);
    return DataAccessUtils.optionalResult(matches);
  }

  @Override
  public List<AuthorizedCompany> findAuthorizedCompanies(UUID userId) {
    return jdbc.query(
        """
        SELECT c.id, c.name, c.business_name, m.role, m.status AS membership_status, c.status
        FROM company_memberships m JOIN companies c ON c.id = m.company_id
        WHERE m.user_id = ? AND m.status = 'ACTIVE' AND c.status = 'ACTIVE'
        ORDER BY c.name, c.id
        """,
        (rs, row) ->
            new AuthorizedCompany(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("business_name"),
                CompanyRole.valueOf(rs.getString("role")),
                MembershipStatus.valueOf(rs.getString("membership_status")),
                rs.getString("status")),
        userId);
  }

  @Override
  public void lockMembershipCreation(UUID userId) {
    // Distinct from the per-company lock for team role and status changes.
    jdbc.query(
        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
        row -> {},
        "sanigest.company.membership-creation:" + userId);
  }

  @Override
  public boolean lockActiveMembership(UUID companyId, UUID userId) {
    return !jdbc.query(
            """
            SELECT m.id FROM company_memberships m
            WHERE m.company_id = ? AND m.user_id = ? AND m.status = 'ACTIVE'
              AND EXISTS (SELECT 1 FROM companies c WHERE c.id = m.company_id AND c.status = 'ACTIVE')
            FOR UPDATE
            """,
            (rs, row) -> rs.getObject("id", UUID.class),
            companyId,
            userId)
        .isEmpty();
  }

  @Override
  public Optional<Membership> findByUserIdForUpdate(UUID userId) {
    return singularOperationalMembership(userId, operationalMembership(userId, null));
  }

  @Override
  public Optional<Membership> findActiveMembershipForUpdate(UUID userId, UUID companyId) {
    return DataAccessUtils.optionalResult(
        operationalMembership(userId, java.util.Objects.requireNonNull(companyId)));
  }

  private List<Membership> operationalMembership(UUID userId, UUID companyId) {
    return jdbc.query(
        """
        SELECT m.id, m.company_id, m.user_id, m.role, m.status FROM company_memberships m
        WHERE m.user_id = ? %s AND m.status = 'ACTIVE'
          AND EXISTS (SELECT 1 FROM companies c WHERE c.id = m.company_id AND c.status = 'ACTIVE')
        ORDER BY m.company_id
        FETCH FIRST 2 ROWS ONLY
        FOR UPDATE
        """
            .formatted(companyId == null ? "" : "AND m.company_id = ?"),
        JdbcMembershipRepository::membership,
        companyId == null ? new Object[] {userId} : new Object[] {userId, companyId});
  }

  @Override
  public List<Membership> findAllByCompanyId(UUID companyId) {
    return jdbc.query(
        """
        SELECT id, company_id, user_id, role, status FROM company_memberships
        WHERE company_id = ? ORDER BY created_at, id
        """,
        JdbcMembershipRepository::membership,
        companyId);
  }

  @Override
  public void lockMembershipChanges(UUID companyId) {
    // Serialize role and status changes without reversing the profile flow's row lock order.
    jdbc.query(
        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
        row -> {},
        "sanigest.company.membership:" + companyId);
  }

  @Override
  public Optional<Membership> findByCompanyIdAndUserId(UUID companyId, UUID userId) {
    return DataAccessUtils.optionalResult(
        jdbc.query(
            """
            SELECT id, company_id, user_id, role, status
            FROM company_memberships WHERE company_id = ? AND user_id = ?
            """,
            JdbcMembershipRepository::membership,
            companyId,
            userId));
  }

  @Override
  public boolean hasPermission(Membership membership, String permission) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM membership_permissions mp
              JOIN company_permissions cp ON cp.id = mp.permission_id
              WHERE mp.membership_id = ? AND cp.company_id = ? AND cp.permission = ?
            )
            """,
            Boolean.class,
            membership.id(),
            membership.companyId(),
            permission));
  }

  @Override
  public boolean hasOtherActiveOwner(UUID companyId, UUID userId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM company_memberships
              WHERE company_id = ? AND user_id <> ? AND role = 'OWNER' AND status = 'ACTIVE'
            )
            """,
            Boolean.class,
            companyId,
            userId));
  }

  @Override
  public void updateRole(Membership membership, CompanyRole role) {
    jdbc.update(
        """
        UPDATE company_memberships SET role = ?, updated_at = CURRENT_TIMESTAMP
        WHERE company_id = ? AND id = ?
        """,
        role.name(),
        membership.companyId(),
        membership.id());
  }

  @Override
  public void revoke(Membership membership, Instant now) {
    jdbc.update(
        """
        UPDATE company_memberships SET status = 'REVOKED', updated_at = ?
        WHERE company_id = ? AND id = ?
        """,
        now.atOffset(ZoneOffset.UTC),
        membership.companyId(),
        membership.id());
  }

  @Override
  public boolean revokedBefore(UUID companyId, UUID userId, Instant invitationCreatedAt) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            SELECT EXISTS (SELECT 1 FROM company_memberships
              WHERE company_id = ? AND user_id = ? AND status = 'REVOKED' AND updated_at < ?)
            """,
            Boolean.class,
            companyId,
            userId,
            invitationCreatedAt.atOffset(ZoneOffset.UTC)));
  }

  @Override
  public boolean existsByUserId(UUID userId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM company_memberships WHERE user_id = ?)",
            Boolean.class,
            userId));
  }

  @Override
  public boolean existsByUserIdOutsideCompany(UUID userId, UUID companyId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM company_memberships WHERE user_id = ? AND company_id <> ?)",
            Boolean.class,
            userId,
            companyId));
  }

  @Override
  public void reinstate(Membership membership) {
    jdbc.update("DELETE FROM membership_permissions WHERE membership_id = ?", membership.id());
    updateRole(membership, membership.role());
    updateStatus(membership, MembershipStatus.ACTIVE);
  }

  @Override
  public void updateStatus(Membership membership, MembershipStatus status) {
    jdbc.update(
        """
        UPDATE company_memberships SET status = ?, updated_at = CURRENT_TIMESTAMP
        WHERE company_id = ? AND id = ?
        """,
        status.name(),
        membership.companyId(),
        membership.id());
  }

  @Override
  public List<Membership> findBatch(
      UUID companyId,
      CompanyRole role,
      MembershipStatus status,
      boolean descending,
      UUID after,
      int limit,
      long offset) {
    var args = new ArrayList<>();
    String filter = filter(args, companyId, role, status);
    if (after != null) {
      filter += descending ? " AND id < ?" : " AND id > ?";
      args.add(after);
    }
    args.add(limit);
    args.add(offset);
    return jdbc.query(
        "SELECT id, company_id, user_id, role, status FROM company_memberships"
            + filter
            + (descending ? " ORDER BY id DESC" : " ORDER BY id ASC")
            + " LIMIT ? OFFSET ?",
        JdbcMembershipRepository::membership,
        args.toArray());
  }

  @Override
  public long count(UUID companyId, CompanyRole role, MembershipStatus status) {
    var args = new ArrayList<>();
    String filter = filter(args, companyId, role, status);
    return java.util.Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT count(*) FROM company_memberships" + filter, Long.class, args.toArray()));
  }

  private static String filter(
      List<Object> args, UUID companyId, CompanyRole role, MembershipStatus status) {
    args.add(companyId);
    String filter = " WHERE company_id = ?";
    if (role != null) {
      filter += " AND role = ?";
      args.add(role.name());
    }
    if (status != null) {
      filter += " AND status = ?";
      args.add(status.name());
    }
    return filter;
  }

  @Override
  public void insert(Membership member) {
    try {
      jdbc.update(
          """
          INSERT INTO company_memberships (id, company_id, user_id, role, status)
          VALUES (?, ?, ?, ?, ?)
          """,
          member.id(),
          member.companyId(),
          member.userId(),
          member.role().name(),
          member.status().name());
    } catch (DuplicateKeyException duplicate) {
      throw new CompanyConflictException("User already belongs to a company", duplicate);
    }
  }

  private static Membership membership(ResultSet rs, int row) throws SQLException {
    return new Membership(
        rs.getObject("id", UUID.class),
        rs.getObject("company_id", UUID.class),
        rs.getObject("user_id", UUID.class),
        CompanyRole.valueOf(rs.getString("role")),
        MembershipStatus.valueOf(rs.getString("status")));
  }

  @Override
  public Optional<CompanyMembership> findCompanyByUserId(UUID userId) {
    return singularOperationalMembership(userId, operationalProfile(userId, null));
  }

  @Override
  public Optional<CompanyMembership> findCompanyByUserIdAndCompanyId(UUID userId, UUID companyId) {
    return DataAccessUtils.optionalResult(
        operationalProfile(userId, java.util.Objects.requireNonNull(companyId)));
  }

  private List<CompanyMembership> operationalProfile(UUID userId, UUID companyId) {
    return jdbc.query(
        """
        SELECT m.status AS membership_status, c.id, c.name, c.business_name,
               c.rnc, c.location, c.currency, c.status
        FROM company_memberships m
        JOIN companies c ON c.id = m.company_id
        WHERE m.user_id = ? %s AND m.status = 'ACTIVE' AND c.status = 'ACTIVE'
        FETCH FIRST 2 ROWS ONLY
        """
            .formatted(companyId == null ? "" : "AND m.company_id = ?"),
        (rs, rowNum) ->
            new CompanyMembership(
                MembershipStatus.valueOf(rs.getString("membership_status")),
                new CompanyProfile(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("business_name"),
                    rs.getString("rnc"),
                    rs.getString("location"),
                    rs.getString("currency"),
                    rs.getString("status"))),
        companyId == null ? new Object[] {userId} : new Object[] {userId, companyId});
  }

  private <T> Optional<T> singularOperationalMembership(UUID userId, List<T> matches) {
    if (matches.size() > 1) {
      throw new CompanySelectionRequiredException();
    }
    if (matches.isEmpty() && existsByUserId(userId)) {
      throw new CompanyAccessDeniedException();
    }
    return DataAccessUtils.optionalResult(matches);
  }
}
