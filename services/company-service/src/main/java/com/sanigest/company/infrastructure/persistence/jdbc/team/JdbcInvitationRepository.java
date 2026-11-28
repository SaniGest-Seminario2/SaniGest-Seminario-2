package com.sanigest.company.infrastructure.persistence.jdbc.team;

import com.sanigest.company.application.exception.profile.CompanyConflictException;
import com.sanigest.company.application.port.out.persistence.team.InvitationRepository;
import com.sanigest.company.domain.enums.team.CompanyRole;
import com.sanigest.company.domain.enums.team.InvitationStatus;
import com.sanigest.company.domain.model.team.Invitation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcInvitationRepository implements InvitationRepository {
  private static final String SELECT =
      "SELECT id, company_id, email, role, status, created_at, expires_at, recipient_name "
          + "FROM company_invitations ";
  private final JdbcTemplate jdbc;

  JdbcInvitationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Invitation> findPendingForUpdate(UUID companyId, String email) {
    return find("company_id = ? AND email = ? AND pending_slot = 1", companyId, email);
  }

  @Override
  public Optional<Invitation> findByTokenHashForUpdate(String tokenHash) {
    return find("token_hash = ?", tokenHash);
  }

  @Override
  public Optional<UUID> findCompanyByTokenHash(String hash) {
    return DataAccessUtils.optionalResult(
        jdbc.query(
            "SELECT company_id FROM company_invitations WHERE token_hash = ?",
            (rs, row) -> rs.getObject("company_id", UUID.class),
            hash));
  }

  @Override
  public Optional<Invitation> findByCompanyAndIdForUpdate(UUID companyId, UUID id) {
    return find("company_id = ? AND id = ?", companyId, id);
  }

  @Override
  public List<Invitation> findPending(UUID companyId, Instant now, int limit, long offset) {
    return jdbc.query(
        SELECT
            + """
            WHERE company_id = ? AND status = 'PENDING' AND expires_at > ?
            ORDER BY created_at, id LIMIT ? OFFSET ?
            """,
        JdbcInvitationRepository::invitation,
        companyId,
        now.atOffset(ZoneOffset.UTC),
        limit,
        offset);
  }

  private Optional<Invitation> find(String condition, Object... params) {
    return DataAccessUtils.optionalResult(
        jdbc.query(
            SELECT + "WHERE " + condition + " FOR UPDATE",
            JdbcInvitationRepository::invitation,
            params));
  }

  private static Invitation invitation(ResultSet rs, int row) throws SQLException {
    return new Invitation(
        rs.getObject("id", UUID.class),
        rs.getObject("company_id", UUID.class),
        rs.getString("email"),
        CompanyRole.valueOf(rs.getString("role")),
        InvitationStatus.valueOf(rs.getString("status")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
        rs.getString("recipient_name"));
  }

  @Override
  public void insert(Invitation invitation, String tokenHash) {
    try {
      jdbc.update(
          """
          INSERT INTO company_invitations
            (id, company_id, email, role, token_hash, status, pending_slot, created_at, expires_at, recipient_name)
          VALUES (?, ?, ?, ?, ?, 'PENDING', 1, ?, ?, ?)
          """,
          invitation.id(),
          invitation.companyId(),
          invitation.email(),
          invitation.role().name(),
          tokenHash,
          invitation.createdAt().atOffset(ZoneOffset.UTC),
          invitation.expiresAt().atOffset(ZoneOffset.UTC),
          invitation.name());
    } catch (DuplicateKeyException duplicate) {
      throw new CompanyConflictException("An invitation is already pending", duplicate);
    }
  }

  @Override
  public void closePending(UUID id, InvitationStatus status) {
    int updated =
        jdbc.update(
            """
            UPDATE company_invitations SET status = ?, pending_slot = NULL
            WHERE id = ? AND status = 'PENDING'
            """,
            status.name(),
            id);
    if (updated != 1) {
      throw new IllegalStateException("Expected a locked pending invitation");
    }
  }
}
