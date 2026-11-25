package com.sanigest.company.application.usecase.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sanigest.company.application.dto.CompanyMembership;
import com.sanigest.company.application.dto.CompanyProfile;
import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.LastOwnerException;
import com.sanigest.company.application.exception.MemberNotFoundException;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.application.usecase.audit.CompanyAuditRecorder;
import com.sanigest.company.domain.enums.AuditOperation;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import com.sanigest.company.domain.model.Membership;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CompanyMembershipUseCasesTest {
  private final UUID company = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID target = UUID.randomUUID();
  private final MembershipRepository memberships = mock(MembershipRepository.class);
  private final CompanyAuditRecorder audit = mock(CompanyAuditRecorder.class);
  private final CompanyMembershipPolicy policy = new CompanyMembershipPolicy(memberships, audit);
  private final ChangeCompanyMemberRoleUseCase change =
      new ChangeCompanyMemberRoleUseCase(policy, memberships, audit);
  private final RevokeCompanyMembershipUseCase revoke =
      new RevokeCompanyMembershipUseCase(policy, memberships, audit, java.time.Clock.systemUTC());

  @ParameterizedTest
  @CsvSource({"OWNER, OWNER", "OWNER, ADMIN", "ADMIN, ADMIN", "ADMIN, OPERATOR"})
  void changesAllowedRoles(CompanyRole actorRole, CompanyRole newRole) {
    actor(actorRole, MembershipStatus.ACTIVE);
    var member = member(target, CompanyRole.VIEWER, MembershipStatus.ACTIVE);

    assertThat(change.change(actor, target, newRole).role()).isEqualTo(newRole);
    var order = inOrder(memberships);
    order.verify(memberships).lockMembershipChanges(company);
    order.verify(memberships).findByCompanyIdAndUserId(company, actor);
    order.verify(memberships).findByCompanyIdAndUserId(company, target);
    order.verify(memberships).updateRole(member, newRole);
    verify(audit)
        .succeeded(
            actor, member, AuditOperation.ROLE_CHANGED, CompanyRole.VIEWER, newRole, null, null);
  }

  @ParameterizedTest
  @CsvSource({"VIEWER, ACTIVE", "OPERATOR, ACTIVE", "OWNER, REVOKED", "ADMIN, SUSPENDED"})
  void deniesSelfElevationAndRevocationForUnauthorizedActors(
      CompanyRole role, MembershipStatus status) {
    actor(role, status);
    assertThatThrownBy(() -> change.change(actor, actor, CompanyRole.ADMIN))
        .isInstanceOf(CompanyAccessDeniedException.class);
    assertThatThrownBy(() -> revoke.revoke(actor, target))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verify(memberships, never()).updateRole(any(), any());
    verify(memberships, never()).revoke(any(), any());
  }

  @Test
  void adminCannotAssignOwnerOrManageAnOwner() {
    actor(CompanyRole.ADMIN, MembershipStatus.ACTIVE);
    member(target, CompanyRole.OWNER, MembershipStatus.ACTIVE);

    assertThatThrownBy(() -> change.change(actor, actor, CompanyRole.OWNER))
        .isInstanceOf(CompanyAccessDeniedException.class);
    assertThatThrownBy(() -> change.change(actor, target, CompanyRole.VIEWER))
        .isInstanceOf(CompanyAccessDeniedException.class);
    assertThatThrownBy(() -> revoke.revoke(actor, target))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verify(memberships, never()).updateRole(any(), any());
    verify(memberships, never()).revoke(any(), any());
  }

  @Test
  void preservesLastOwnerAndAllowsDemotionWhenAnotherOwnerIsActive() {
    actor(CompanyRole.OWNER, MembershipStatus.ACTIVE);

    assertThatThrownBy(() -> revoke.revoke(actor, actor)).isInstanceOf(LastOwnerException.class);
    assertThatThrownBy(() -> change.change(actor, actor, CompanyRole.ADMIN))
        .isInstanceOf(LastOwnerException.class);
    verify(memberships, never()).updateRole(any(), any());
    verify(memberships, never()).revoke(any(), any());

    when(memberships.hasOtherActiveOwner(company, actor)).thenReturn(true);
    assertThat(change.change(actor, actor, CompanyRole.ADMIN).role()).isEqualTo(CompanyRole.ADMIN);
  }

  @Test
  void revokesSuspendedMemberButRejectsRoleChangeAndMissingOrRevokedTargets() {
    actor(CompanyRole.ADMIN, MembershipStatus.ACTIVE);
    assertThatThrownBy(() -> revoke.revoke(actor, target))
        .isInstanceOf(MemberNotFoundException.class);

    var suspended = member(target, CompanyRole.OPERATOR, MembershipStatus.SUSPENDED);
    assertThatThrownBy(() -> change.change(actor, target, CompanyRole.VIEWER))
        .isInstanceOf(MemberNotFoundException.class);
    revoke.revoke(actor, target);
    verify(memberships).revoke(org.mockito.ArgumentMatchers.eq(suspended), any());

    member(target, CompanyRole.OPERATOR, MembershipStatus.REVOKED);
    assertThatThrownBy(() -> revoke.revoke(actor, target))
        .isInstanceOf(MemberNotFoundException.class);
    verify(memberships, never()).updateRole(any(), any());
  }

  @Test
  void acceptsUnchangedOwnerRoleWithoutUpdatingTheMembership() {
    actor(CompanyRole.OWNER, MembershipStatus.ACTIVE);
    assertThat(change.change(actor, actor, CompanyRole.OWNER).role()).isEqualTo(CompanyRole.OWNER);
    verify(memberships, never()).updateRole(any(), any());
  }

  @Test
  void rejectedAuditFailurePreservesBusinessRejection() {
    actor(CompanyRole.OWNER, MembershipStatus.ACTIVE);
    doThrow(new IllegalStateException("storage failure"))
        .when(audit)
        .rejected(company, actor, actor, AuditOperation.MEMBER_REVOKED);

    assertThatThrownBy(() -> revoke.revoke(actor, actor)).isInstanceOf(LastOwnerException.class);
    verify(memberships, never()).revoke(any(), any());
  }

  @Test
  void suspendsAndReactivatesMembersWithoutChangingTheirRole() {
    actor(CompanyRole.ADMIN, MembershipStatus.ACTIVE);
    var changeStatus = new ChangeCompanyMemberStatusUseCase(policy, memberships, audit);
    var active = member(target, CompanyRole.OPERATOR, MembershipStatus.ACTIVE);
    var result = changeStatus.change(actor, null, target, MembershipStatus.SUSPENDED);
    assertThat(result.status()).isEqualTo(MembershipStatus.SUSPENDED);
    assertThat(result.role()).isEqualTo(CompanyRole.OPERATOR);
    verify(memberships).updateStatus(active, MembershipStatus.SUSPENDED);
    verify(audit)
        .succeeded(
            actor,
            active,
            AuditOperation.MEMBER_SUSPENDED,
            CompanyRole.OPERATOR,
            CompanyRole.OPERATOR,
            MembershipStatus.ACTIVE,
            MembershipStatus.SUSPENDED);

    var suspended = member(target, CompanyRole.OPERATOR, MembershipStatus.SUSPENDED);
    assertThat(changeStatus.change(actor, null, target, MembershipStatus.ACTIVE).status())
        .isEqualTo(MembershipStatus.ACTIVE);
    verify(memberships).updateStatus(suspended, MembershipStatus.ACTIVE);
  }

  @Test
  void preventsSuspensionOfLastOwnerAndReactivationOfRevokedMembership() {
    actor(CompanyRole.OWNER, MembershipStatus.ACTIVE);
    var changeStatus = new ChangeCompanyMemberStatusUseCase(policy, memberships, audit);
    assertThatThrownBy(() -> changeStatus.change(actor, null, actor, MembershipStatus.SUSPENDED))
        .isInstanceOf(LastOwnerException.class);
    member(target, CompanyRole.ADMIN, MembershipStatus.REVOKED);
    assertThatThrownBy(() -> changeStatus.change(actor, null, target, MembershipStatus.ACTIVE))
        .isInstanceOf(MemberNotFoundException.class);
    assertThatThrownBy(() -> changeStatus.change(actor, null, target, MembershipStatus.REVOKED))
        .isInstanceOf(IllegalArgumentException.class);
    verify(memberships, never()).updateStatus(any(), any());
  }

  private void actor(CompanyRole role, MembershipStatus status) {
    var profile =
        new CompanyProfile(company, "Legal SRL", "Comercial", null, null, "DOP", "ACTIVE");
    when(memberships.findCompanyByUserId(actor))
        .thenReturn(Optional.of(new CompanyMembership(status, profile)));
    member(actor, role, status);
  }

  private Membership member(UUID user, CompanyRole role, MembershipStatus status) {
    var membership = new Membership(UUID.randomUUID(), company, user, role, status);
    when(memberships.findByCompanyIdAndUserId(company, user)).thenReturn(Optional.of(membership));
    return membership;
  }
}
