package com.sanigest.company.infrastructure.persistence;

import com.sanigest.company.application.GetMyCompanyUseCase.CompanyMembership;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyProfile;
import com.sanigest.company.application.GetMyCompanyUseCase.MembershipRepository;
import com.sanigest.company.domain.model.CompanyRole;
import com.sanigest.company.domain.model.Membership;
import com.sanigest.company.domain.model.MembershipStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
  public Optional<Membership> findByUserIdForUpdate(UUID userId) {
    return DataAccessUtils.optionalResult(
        jdbc.query(
            "SELECT id, company_id, user_id, role, status FROM company_memberships WHERE user_id = ? FOR UPDATE",
            JdbcMembershipRepository::membership,
            userId));
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
  public void revoke(Membership membership) {
    jdbc.update(
        """
        UPDATE company_memberships SET status = 'REVOKED', updated_at = CURRENT_TIMESTAMP
        WHERE company_id = ? AND id = ?
        """,
        membership.companyId(),
        membership.id());
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
  public void insert(Membership member) {
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
    return DataAccessUtils.optionalResult(
        jdbc.query(
            """
            SELECT m.status AS membership_status, c.id, c.name, c.business_name,
                   c.rnc, c.location, c.currency, c.status
            FROM company_memberships m
            JOIN companies c ON c.id = m.company_id
            WHERE m.user_id = ?
            """,
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
            userId));
  }
}
