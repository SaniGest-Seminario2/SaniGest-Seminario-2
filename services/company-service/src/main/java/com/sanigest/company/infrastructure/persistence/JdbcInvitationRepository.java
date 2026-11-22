package com.sanigest.company.infrastructure.persistence;

import com.sanigest.company.domain.model.CompanyRole;
import com.sanigest.company.domain.model.Invitation;
import com.sanigest.company.domain.model.Invitation.Status;
import com.sanigest.company.domain.port.InvitationRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcInvitationRepository implements InvitationRepository {
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

  private Optional<Invitation> find(String condition, Object... params) {
    return DataAccessUtils.optionalResult(
        jdbc.query(
            "SELECT id, company_id, email, role, status, created_at, expires_at FROM company_invitations "
                + "WHERE "
                + condition
                + " FOR UPDATE",
            (rs, row) ->
                new Invitation(
                    rs.getObject("id", UUID.class),
                    rs.getObject("company_id", UUID.class),
                    rs.getString("email"),
                    CompanyRole.valueOf(rs.getString("role")),
                    Status.valueOf(rs.getString("status")),
                    rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                    rs.getObject("expires_at", OffsetDateTime.class).toInstant()),
            params));
  }

  @Override
  public void insert(Invitation invitation, String tokenHash) {
    jdbc.update(
        """
        INSERT INTO company_invitations
          (id, company_id, email, role, token_hash, status, pending_slot, created_at, expires_at)
        VALUES (?, ?, ?, ?, ?, 'PENDING', 1, ?, ?)
        """,
        invitation.id(),
        invitation.companyId(),
        invitation.email(),
        invitation.role().name(),
        tokenHash,
        invitation.createdAt().atOffset(ZoneOffset.UTC),
        invitation.expiresAt().atOffset(ZoneOffset.UTC));
  }

  @Override
  public void closePending(UUID id, Status status) {
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
