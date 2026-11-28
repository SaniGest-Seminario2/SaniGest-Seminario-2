package com.sanigest.company.application.usecase.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.company.application.exception.CompanyConflictException;
import com.sanigest.company.application.exception.InvitationNotFoundException;
import com.sanigest.company.application.port.out.persistence.InvitationRepository;
import com.sanigest.company.application.usecase.audit.CompanyAuditRecorder;
import com.sanigest.company.domain.enums.AuditOperation;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.InvitationStatus;
import com.sanigest.company.domain.model.Invitation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManageCompanyInvitationsUseCaseTest {
  private static final Instant NOW = Instant.parse("2026-11-22T00:00:00Z");
  private final UUID actor = UUID.randomUUID();
  private final UUID company = UUID.randomUUID();
  private final UUID invitationId = UUID.randomUUID();
  private final CompanyMembershipPolicy policy = mock(CompanyMembershipPolicy.class);
  private final InvitationRepository invitations = mock(InvitationRepository.class);
  private final CompanyAuditRecorder audit = mock(CompanyAuditRecorder.class);
  private final ManageCompanyInvitationsUseCase useCase =
      new ManageCompanyInvitationsUseCase(
          policy, invitations, audit, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void listsPendingInvitationsWithBoundedPaginationAndCompanyScope() {
    var invitation = invitation(NOW.plusSeconds(60));
    when(policy.requireAdministrator(actor, company, false)).thenReturn(company);
    when(invitations.findPending(company, NOW, 5, 10)).thenReturn(List.of(invitation));
    assertThat(useCase.pending(actor, company, 2, 5)).containsExactly(invitation);
    assertThatThrownBy(() -> useCase.pending(actor, company, 0, 101))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsExpiredOrMissingInvitationsAndAuditsRevocation() {
    when(policy.requireAdministrator(actor, company, true)).thenReturn(company);
    assertThatThrownBy(() -> useCase.revoke(actor, company, invitationId))
        .isInstanceOf(InvitationNotFoundException.class);
    when(invitations.findByCompanyAndIdForUpdate(company, invitationId))
        .thenReturn(Optional.of(invitation(NOW)));
    assertThatThrownBy(() -> useCase.revoke(actor, company, invitationId))
        .isInstanceOf(CompanyConflictException.class);
    verifyNoInteractions(audit);

    when(invitations.findByCompanyAndIdForUpdate(company, invitationId))
        .thenReturn(Optional.of(invitation(NOW.plusSeconds(60))));
    useCase.revoke(actor, company, invitationId);
    verify(invitations).closePending(invitationId, InvitationStatus.REVOKED);
    verify(audit)
        .invitationChanged(company, actor, invitationId, AuditOperation.INVITATION_REVOKED);
  }

  private Invitation invitation(Instant expiresAt) {
    return new Invitation(
        invitationId,
        company,
        "guest@example.com",
        CompanyRole.VIEWER,
        InvitationStatus.PENDING,
        NOW.minusSeconds(60),
        expiresAt,
        "Invited Name");
  }
}
