package com.sanigest.company.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sanigest.company.application.CompanyMembershipPolicy.LastOwnerException;
import com.sanigest.company.application.CompanyMembershipPolicy.MemberNotFoundException;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyAccessDeniedException;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyMembership;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyProfile;
import com.sanigest.company.application.GetMyCompanyUseCase.MembershipRepository;
import com.sanigest.company.domain.model.CompanyRole;
import com.sanigest.company.domain.model.Membership;
import com.sanigest.company.domain.model.MembershipStatus;
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
  private final CompanyMembershipPolicy policy = new CompanyMembershipPolicy(memberships);
  private final ChangeCompanyMemberRoleUseCase change =
      new ChangeCompanyMemberRoleUseCase(policy, memberships);
  private final RevokeCompanyMembershipUseCase revoke =
      new RevokeCompanyMembershipUseCase(policy, memberships);

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
    verify(memberships, never()).revoke(any());
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
    verify(memberships, never()).revoke(any());
  }

  @Test
  void preservesLastOwnerAndAllowsDemotionWhenAnotherOwnerIsActive() {
    actor(CompanyRole.OWNER, MembershipStatus.ACTIVE);

    assertThatThrownBy(() -> revoke.revoke(actor, actor)).isInstanceOf(LastOwnerException.class);
    assertThatThrownBy(() -> change.change(actor, actor, CompanyRole.ADMIN))
        .isInstanceOf(LastOwnerException.class);
    verify(memberships, never()).updateRole(any(), any());
    verify(memberships, never()).revoke(any());

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
    verify(memberships).revoke(suspended);

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
