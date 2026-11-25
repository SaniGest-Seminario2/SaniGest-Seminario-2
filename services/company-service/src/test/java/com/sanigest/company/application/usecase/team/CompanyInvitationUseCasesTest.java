package com.sanigest.company.application.usecase.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.company.application.dto.CompanyMembership;
import com.sanigest.company.application.dto.CompanyProfile;
import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.CompanyConflictException;
import com.sanigest.company.application.port.out.integration.MemberIdentityDirectory;
import com.sanigest.company.application.port.out.integration.MemberIdentityDirectory.Identity;
import com.sanigest.company.application.port.out.persistence.CompanyRepository;
import com.sanigest.company.application.port.out.persistence.InvitationRepository;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.application.usecase.audit.CompanyAuditRecorder;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.InvitationStatus;
import com.sanigest.company.domain.enums.MembershipStatus;
import com.sanigest.company.domain.model.Company;
import com.sanigest.company.domain.model.Invitation;
import com.sanigest.company.domain.model.Membership;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
  private final CompanyAuditRecorder audit = mock(CompanyAuditRecorder.class);
  private final MemberIdentityDirectory directory = mock(MemberIdentityDirectory.class);
  private final InviteCompanyUserUseCase invite =
      new InviteCompanyUserUseCase(
          new CompanyMembershipPolicy(members, audit),
          invitations,
          Duration.ofHours(4),
          clock,
          Optional.empty(),
          audit);
  private final AcceptCompanyInvitationUseCase accept =
      new AcceptCompanyInvitationUseCase(
          invitations, members, companies, clock, audit, directory, false);
  private final String token = InvitationToken.generate();

  @ParameterizedTest
  @EnumSource(
      value = CompanyRole.class,
      names = {"OPERATOR", "VIEWER"})
  void createsPendingInvitationWithNormalizedRecipientAndConfiguredExpiry(CompanyRole role) {
    authorize(CompanyRole.ADMIN, MembershipStatus.ACTIVE);
    var result =
        invite.invite(owner, null, " Guest@Example.com ", role, false, "  María Rodríguez  ");
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
                InvitationStatus.PENDING,
                NOW,
                NOW.plusSeconds(14400),
                "María Rodríguez"));
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
                InvitationStatus.PENDING,
                NOW,
                NOW.plusSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new Invitation(
                UUID.randomUUID(),
                companyId,
                EMAIL,
                CompanyRole.OWNER,
                InvitationStatus.PENDING,
                NOW,
                NOW.plusSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new Invitation(
                UUID.randomUUID(),
                companyId,
                EMAIL,
                CompanyRole.VIEWER,
                InvitationStatus.PENDING,
                NOW,
                NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(invitation(InvitationStatus.PENDING, NOW).statusAt(NOW))
        .isEqualTo(InvitationStatus.EXPIRED);
  }

  @Test
  void rejectsActiveDuplicateUntilExplicitlyReplaced() {
    authorize(CompanyRole.OWNER, MembershipStatus.ACTIVE);
    var prior = invitation(InvitationStatus.PENDING, NOW.plusSeconds(60));
    when(invitations.findPendingForUpdate(companyId, EMAIL)).thenReturn(Optional.of(prior));
    assertThatThrownBy(() -> invite.invite(owner, EMAIL, CompanyRole.VIEWER, false))
        .isInstanceOf(CompanyConflictException.class);
    var replacement = invite.invite(owner, EMAIL, CompanyRole.VIEWER, true);
    verify(invitations).closePending(prior.id(), InvitationStatus.REVOKED);
    assertThat(replacement.invitationId()).isNotEqualTo(prior.id());
  }

  @Test
  void closesExpiredInvitationWhenCreatingItsSuccessor() {
    authorize(CompanyRole.OWNER, MembershipStatus.ACTIVE);
    var prior = invitation(InvitationStatus.PENDING, NOW);
    when(invitations.findPendingForUpdate(companyId, EMAIL)).thenReturn(Optional.of(prior));
    invite.invite(owner, EMAIL, CompanyRole.VIEWER, false);
    verify(invitations).closePending(prior.id(), InvitationStatus.EXPIRED);
    verify(invitations).insert(any(Invitation.class), any(String.class));
  }

  @Test
  void acceptsForVerifiedRecipientAndInsertsMembershipBeforeConsumingToken() {
    var pending = stored(InvitationStatus.PENDING, NOW.plusSeconds(60));
    when(companies.findById(companyId)).thenReturn(Optional.of(company));
    var member = accept.accept(guest, token);
    assertThat(member)
        .isEqualTo(
            new Membership(member.id(), companyId, guest, pending.role(), MembershipStatus.ACTIVE));
    var order = org.mockito.Mockito.inOrder(members, invitations);
    order.verify(members).insert(member);
    order.verify(invitations).closePending(pending.id(), InvitationStatus.ACCEPTED);
  }

  @Test
  void rejectsUnverifiedOrDifferentRecipientBeforeMembershipAccess() {
    assertThatThrownBy(() -> accept.accept(guest, token))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verifyNoInteractions(invitations);
    stored(InvitationStatus.PENDING, NOW.plusSeconds(60));
    identity("other@example.com", true);
    assertThatThrownBy(() -> accept.accept(guest, token))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verify(members, org.mockito.Mockito.never()).insert(any());
  }

  @Test
  void rejectsExistingMembershipInAnotherCompanyWithoutConsumingToken() {
    stored(InvitationStatus.PENDING, NOW.plusSeconds(60));
    when(members.existsByUserIdOutsideCompany(guest, companyId)).thenReturn(true);
    assertThatThrownBy(() -> accept.accept(guest, token))
        .isInstanceOf(CompanyConflictException.class);
    verify(invitations, org.mockito.Mockito.never())
        .closePending(any(), eq(InvitationStatus.ACCEPTED));
    verify(members, org.mockito.Mockito.never()).insert(any());
  }

  @ParameterizedTest
  @EnumSource(InvitationStatus.class)
  void rejectsExpiredRevokedOrPreviouslyConsumedTokens(InvitationStatus status) {
    stored(status, NOW);
    assertThatThrownBy(() -> accept.accept(guest, token))
        .isInstanceOf(CompanyConflictException.class);
    verifyNoInteractions(companies);
    verify(members, org.mockito.Mockito.never()).insert(any());
  }

  @Test
  void acceptsInAnotherCompanyWhenEnabled() {
    stored(InvitationStatus.PENDING, NOW.plusSeconds(60));
    when(companies.findById(companyId)).thenReturn(Optional.of(company));
    var enabled =
        new AcceptCompanyInvitationUseCase(
            invitations, members, companies, clock, audit, directory, true);
    assertThat(enabled.accept(guest, token).companyId()).isEqualTo(companyId);
    verify(members, org.mockito.Mockito.never()).existsByUserIdOutsideCompany(any(), any());
  }

  @ParameterizedTest
  @EnumSource(
      value = MembershipStatus.class,
      names = {"ACTIVE", "SUSPENDED"})
  void deniesExistingActiveOrSuspendedMembers(MembershipStatus status) {
    stored(InvitationStatus.PENDING, NOW.plusSeconds(60));
    when(members.findByCompanyIdAndUserId(companyId, guest))
        .thenReturn(
            Optional.of(
                new Membership(UUID.randomUUID(), companyId, guest, CompanyRole.OWNER, status)));
    assertThatThrownBy(() -> accept.accept(guest, token))
        .isInstanceOf(CompanyConflictException.class);
    verify(members, org.mockito.Mockito.never()).insert(any());
  }

  @Test
  void reinstatesRevokedMemberWithSameIdAndInvitedRole() {
    stored(InvitationStatus.PENDING, NOW.plusSeconds(60));
    UUID original = UUID.randomUUID();
    when(members.revokedBefore(eq(companyId), eq(guest), any())).thenReturn(true);
    when(members.findByCompanyIdAndUserId(companyId, guest))
        .thenReturn(
            Optional.of(
                new Membership(
                    original, companyId, guest, CompanyRole.OWNER, MembershipStatus.REVOKED)));
    when(companies.findById(companyId)).thenReturn(Optional.of(company));
    var result = accept.accept(guest, token);
    assertThat(result)
        .isEqualTo(
            new Membership(
                original, companyId, guest, CompanyRole.VIEWER, MembershipStatus.ACTIVE));
    verify(members).reinstate(result);
  }

  private void identity(String email, boolean verified) {
    when(directory.lookup(List.of(guest)))
        .thenReturn(Map.of(guest, new Identity("Guest", email, verified)));
  }

  private Invitation stored(InvitationStatus status, Instant expiresAt) {
    var invitation = invitation(status, expiresAt);
    identity(EMAIL, true);
    when(invitations.findCompanyByTokenHash(InvitationToken.hash(token)))
        .thenReturn(Optional.of(companyId));
    when(invitations.findByTokenHashForUpdate(InvitationToken.hash(token)))
        .thenReturn(Optional.of(invitation));
    return invitation;
  }

  private Invitation invitation(InvitationStatus status, Instant expiresAt) {
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
    var profile = new CompanyProfile(companyId, "Clinic", "Clinic", null, null, "DOP", "ACTIVE");
    when(members.findCompanyByUserId(owner))
        .thenReturn(Optional.of(new CompanyMembership(status, profile)));
    when(members.findByCompanyIdAndUserId(companyId, owner))
        .thenReturn(Optional.of(new Membership(UUID.randomUUID(), companyId, owner, role, status)));
    when(companies.findById(companyId)).thenReturn(Optional.of(company));
  }
}
