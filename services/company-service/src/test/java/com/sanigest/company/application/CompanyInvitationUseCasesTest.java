package com.sanigest.company.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.company.application.CreateCompanyUseCase.ConflictException;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyAccessDeniedException;
import com.sanigest.company.application.GetMyCompanyUseCase.MembershipRepository;
import com.sanigest.company.domain.model.Company;
import com.sanigest.company.domain.model.CompanyRole;
import com.sanigest.company.domain.model.Invitation;
import com.sanigest.company.domain.model.Invitation.Status;
import com.sanigest.company.domain.model.Membership;
import com.sanigest.company.domain.model.MembershipStatus;
import com.sanigest.company.domain.port.CompanyRepository;
import com.sanigest.company.domain.port.InvitationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class CompanyInvitationUseCasesTest {
  private static final String EMAIL = "guest@example.com";
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private final UUID companyId = UUID.randomUUID();
  private final UUID owner = UUID.randomUUID();
  private final UUID guest = UUID.randomUUID();
  private final Company company =
      new Company(companyId, "Clinic", "Clinic", null, EMAIL, null, null, "DOP", "ACTIVE");
  private final MembershipRepository members = mock(MembershipRepository.class);
  private final CompanyRepository companies = mock(CompanyRepository.class);
  private final InvitationRepository invitations = mock(InvitationRepository.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final InviteCompanyUserUseCase invite =
      new InviteCompanyUserUseCase(
          members, companies, invitations, Duration.ofHours(4), clock, Optional.empty());
  private final AcceptCompanyInvitationUseCase accept =
      new AcceptCompanyInvitationUseCase(invitations, members, companies, clock);
  private final String token = InvitationToken.generate();

  @ParameterizedTest
  @EnumSource(
      value = CompanyRole.class,
      names = {"OPERATOR", "VIEWER"})
  void createsPendingInvitationWithNormalizedRecipientAndConfiguredExpiry(CompanyRole role) {
    authorize(CompanyRole.ADMIN, MembershipStatus.ACTIVE);
    var result = invite.invite(owner, " Guest@Example.com ", role, false);
    var saved = ArgumentCaptor.forClass(Invitation.class);
    verify(invitations)
        .insert(saved.capture(), org.mockito.ArgumentMatchers.matches("[a-f0-9]{64}"));
    assertThat(saved.getValue())
        .isEqualTo(
            new Invitation(
                result.invitationId(),
                companyId,
                EMAIL,
                role,
                Status.PENDING,
                NOW,
                NOW.plusSeconds(14400)));
  }

  @Test
  void rejectsUnauthorizedMembersBeforeLookingUpInvitations() {
    authorize(CompanyRole.OPERATOR, MembershipStatus.ACTIVE);
    assertThatThrownBy(() -> invite.invite(owner, EMAIL, CompanyRole.VIEWER, false))
        .isInstanceOf(CompanyAccessDeniedException.class);
    authorize(CompanyRole.ADMIN, MembershipStatus.SUSPENDED);
    assertThatThrownBy(() -> invite.invite(owner, EMAIL, CompanyRole.VIEWER, false))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verifyNoInteractions(invitations);
  }

  @Test
  void validatesRecipientRoleAndExpirationInDomain() {
    assertThatThrownBy(() ->
            new Invitation(
                UUID.randomUUID(),
                companyId,
                "invalid",
                CompanyRole.VIEWER,
                Status.PENDING,
                NOW,
                NOW.plusSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new Invitation(
                UUID.randomUUID(),
                companyId,
                EMAIL,
                CompanyRole.OWNER,
                Status.PENDING,
                NOW,
                NOW.plusSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new Invitation(
                UUID.randomUUID(), companyId, EMAIL, CompanyRole.VIEWER, Status.PENDING, NOW, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(invitation(Status.PENDING, NOW).statusAt(NOW)).isEqualTo(Status.EXPIRED);
  }

  @Test
  void rejectsActiveDuplicateUntilExplicitlyReplaced() {
    authorize(CompanyRole.OWNER, MembershipStatus.ACTIVE);
    var prior = invitation(Status.PENDING, NOW.plusSeconds(60));
    when(invitations.findPendingForUpdate(companyId, EMAIL)).thenReturn(Optional.of(prior));
    assertThatThrownBy(() -> invite.invite(owner, EMAIL, CompanyRole.VIEWER, false))
        .isInstanceOf(ConflictException.class);
    var replacement = invite.invite(owner, EMAIL, CompanyRole.VIEWER, true);
    verify(invitations).closePending(prior.id(), Status.REVOKED);
    assertThat(replacement.invitationId()).isNotEqualTo(prior.id());
  }

  @Test
  void closesExpiredInvitationWhenCreatingItsSuccessor() {
    authorize(CompanyRole.OWNER, MembershipStatus.ACTIVE);
    var prior = invitation(Status.PENDING, NOW);
    when(invitations.findPendingForUpdate(companyId, EMAIL)).thenReturn(Optional.of(prior));
    invite.invite(owner, EMAIL, CompanyRole.VIEWER, false);
    verify(invitations).closePending(prior.id(), Status.EXPIRED);
    verify(invitations).insert(any(Invitation.class), any(String.class));
  }

  @Test
  void acceptsForVerifiedRecipientAndInsertsMembershipBeforeConsumingToken() {
    var pending = stored(Status.PENDING, NOW.plusSeconds(60));
    when(companies.findByIdForUpdate(companyId)).thenReturn(Optional.of(company));
    var member = accept.accept(guest, "Guest@Example.com", token);
    assertThat(member)
        .isEqualTo(
            new Membership(member.id(), companyId, guest, pending.role(), MembershipStatus.ACTIVE));
    var order = org.mockito.Mockito.inOrder(members, invitations);
    order.verify(members).insert(member);
    order.verify(invitations).closePending(pending.id(), Status.ACCEPTED);
  }

  @Test
  void rejectsUnverifiedOrDifferentRecipientBeforeMembershipAccess() {
    assertThatThrownBy(() -> accept.accept(guest, null, token))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verifyNoInteractions(invitations);
    stored(Status.PENDING, NOW.plusSeconds(60));
    assertThatThrownBy(() -> accept.accept(guest, "other@example.com", token))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verifyNoInteractions(members);
  }

  @Test
  void rejectsExistingMembershipInAnotherCompanyWithoutConsumingToken() {
    stored(Status.PENDING, NOW.plusSeconds(60));
    when(members.existsByUserId(guest)).thenReturn(true);
    assertThatThrownBy(() -> accept.accept(guest, EMAIL, token))
        .isInstanceOf(ConflictException.class);
    verify(invitations, org.mockito.Mockito.never()).closePending(any(), eq(Status.ACCEPTED));
    verify(members, org.mockito.Mockito.never()).insert(any());
  }

  @ParameterizedTest
  @EnumSource(Status.class)
  void rejectsExpiredRevokedOrPreviouslyConsumedTokens(Status status) {
    stored(status, NOW);
    assertThatThrownBy(() -> accept.accept(guest, EMAIL, token))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(members, companies);
  }

  private Invitation stored(Status status, Instant expiresAt) {
    var invitation = invitation(status, expiresAt);
    when(invitations.findByTokenHashForUpdate(InvitationToken.hash(token)))
        .thenReturn(Optional.of(invitation));
    return invitation;
  }

  private Invitation invitation(Status status, Instant expiresAt) {
    return new Invitation(
        UUID.randomUUID(),
        companyId,
        EMAIL,
        CompanyRole.VIEWER,
        status,
        NOW.minusSeconds(30),
        expiresAt);
  }

  private void authorize(CompanyRole role, MembershipStatus status) {
    when(members.findByUserIdForUpdate(owner))
        .thenReturn(Optional.of(new Membership(UUID.randomUUID(), companyId, owner, role, status)));
    when(companies.findById(companyId)).thenReturn(Optional.of(company));
  }
}
