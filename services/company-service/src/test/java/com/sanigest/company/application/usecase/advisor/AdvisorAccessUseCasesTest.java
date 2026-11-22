package com.sanigest.company.application.usecase.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.company.application.dto.CompanyMembership;
import com.sanigest.company.application.dto.CompanyProfile;
import com.sanigest.company.application.exception.AuthorizationUnavailableException;
import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.CompanyConflictException;
import com.sanigest.company.application.port.out.integration.AdvisorEligibility;
import com.sanigest.company.application.port.out.persistence.AdvisorAccessRepository;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.application.usecase.audit.CompanyAuditRecorder;
import com.sanigest.company.domain.enums.AuditOperation;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import com.sanigest.company.domain.model.AdvisorAccess;
import com.sanigest.company.domain.model.AdvisorPermission;
import com.sanigest.company.domain.model.AdvisorPermission.Module;
import com.sanigest.company.domain.model.AdvisorPermission.Scope;
import com.sanigest.company.domain.model.Membership;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class AdvisorAccessUseCasesTest {
  private static final AdvisorPermission READ = new AdvisorPermission(Module.CATALOG, Scope.READ);
  private static final AdvisorPermission WRITE = new AdvisorPermission(Module.CATALOG, Scope.WRITE);
  private static final Instant NOW = Instant.parse("2026-11-22T15:00:00Z");
  private final UUID companyId = UUID.randomUUID();
  private final UUID actorId = UUID.randomUUID();
  private final UUID advisorId = UUID.randomUUID();
  private final MembershipRepository memberships = mock(MembershipRepository.class);
  private final AdvisorAccessRepository access = mock(AdvisorAccessRepository.class);
  private final AdvisorEligibility eligibility = mock(AdvisorEligibility.class);
  private final CompanyAuditRecorder audit = mock(CompanyAuditRecorder.class);
  private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
  private final AdvisorOwnerPolicy owners = new AdvisorOwnerPolicy(memberships);
  private final AdvisorAccessChanges changes =
      new AdvisorAccessChanges(access, audit, Clock.fixed(NOW, ZoneOffset.UTC));
  private final PutAdvisorAccessUseCase put =
      new PutAdvisorAccessUseCase(
          owners, audit, eligibility, access, changes, new TransactionTemplate(manager));
  private final RevokeAdvisorAccessUseCase revoke =
      new RevokeAdvisorAccessUseCase(owners, access, changes);
  private final GetAdvisorAccessUseCase get = new GetAdvisorAccessUseCase(owners, access);

  @BeforeEach
  void setup() {
    var profile =
        new CompanyProfile(companyId, "Company", "Company SRL", null, null, "DOP", "ACTIVE");
    when(memberships.findCompanyByUserId(actorId))
        .thenReturn(Optional.of(new CompanyMembership(MembershipStatus.ACTIVE, profile)));
    role(CompanyRole.OWNER);
    when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
  }

  @ParameterizedTest
  @EnumSource(
      value = CompanyRole.class,
      names = {"ADMIN", "OPERATOR", "VIEWER"})
  void onlyOwnersManageAccess(CompanyRole role) {
    role(role);
    assertThatThrownBy(() -> put.put(actorId, advisorId, 0, List.of(READ)))
        .isInstanceOf(CompanyAccessDeniedException.class);
    assertThatThrownBy(() -> get.get(actorId)).isInstanceOf(CompanyAccessDeniedException.class);
    assertThatThrownBy(() -> revoke.revoke(actorId, advisorId))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verifyNoInteractions(eligibility, access);
  }

  @Test
  void ownerCanGrantListAndRevokeAnExplicitPermission() {
    when(eligibility.isEligible(advisorId)).thenReturn(true);
    var granted = new AdvisorAccess(advisorId, 1, List.of(READ));
    when(access.find(companyId, advisorId))
        .thenReturn(new AdvisorAccess(advisorId, 0, List.of()), granted);
    when(access.list(companyId)).thenReturn(List.of(granted));
    assertThat(put.put(actorId, advisorId, 0, List.of(READ))).isEqualTo(granted);
    assertThat(get.get(actorId)).containsExactly(granted);
    revoke.revoke(actorId, advisorId);
    verify(access).grant(companyId, advisorId, READ, NOW);
    verify(access).revoke(companyId, advisorId, READ, NOW);
    verify(audit)
        .advisorChanged(companyId, actorId, advisorId, AuditOperation.ADVISOR_GRANTED, READ);
    verify(audit)
        .advisorChanged(companyId, actorId, advisorId, AuditOperation.ADVISOR_REVOKED, READ);
  }

  @Test
  void repetitionsAreIdempotentAndStaleWritesCannotRestoreAccess() {
    when(eligibility.isEligible(advisorId)).thenReturn(true);
    var granted = new AdvisorAccess(advisorId, 1, List.of(READ));
    var revoked = new AdvisorAccess(advisorId, 2, List.of());
    when(access.find(companyId, advisorId)).thenReturn(granted, revoked, revoked);
    assertThat(put.put(actorId, advisorId, 0, List.of(READ))).isEqualTo(granted);
    revoke.revoke(actorId, advisorId);
    assertThatThrownBy(() -> put.put(actorId, advisorId, 1, List.of(READ, WRITE)))
        .isInstanceOf(CompanyConflictException.class);
    verify(access, never()).saveRevision(any(), any(), org.mockito.ArgumentMatchers.anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void selfGrantAndIneligibleRecipientAreRejectedBeforeWriting() {
    assertThatThrownBy(() -> put.put(actorId, actorId, 0, List.of(READ)))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verifyNoInteractions(eligibility);
    assertThatThrownBy(() -> put.put(actorId, advisorId, 0, List.of(READ)))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verifyNoInteractions(access, manager);
  }

  @Test
  void failedEligibilityCannotCreateGrants() {
    when(eligibility.isEligible(advisorId)).thenThrow(new AuthorizationUnavailableException());
    assertThatThrownBy(() -> put.put(actorId, advisorId, 0, List.of(READ)))
        .isInstanceOf(AuthorizationUnavailableException.class);
    verifyNoInteractions(access, audit, manager);
  }

  @Test
  void accessChecksAreScopedAndReadDoesNotAuthorizeWrite() {
    var check = new CheckAdvisorPermissionUseCase(eligibility, access);
    UUID otherCompany = UUID.randomUUID();
    when(eligibility.isEligible(advisorId)).thenReturn(true);
    when(access.allows(companyId, advisorId, READ)).thenReturn(true, false);
    check.require(advisorId, companyId, READ);
    assertThatThrownBy(() -> check.require(advisorId, companyId, WRITE))
        .isInstanceOf(CompanyAccessDeniedException.class);
    assertThatThrownBy(() -> check.require(advisorId, otherCompany, READ))
        .isInstanceOf(CompanyAccessDeniedException.class);
    assertThatThrownBy(() -> check.require(advisorId, companyId, READ))
        .isInstanceOf(CompanyAccessDeniedException.class);
  }

  private void role(CompanyRole role) {
    when(memberships.findByCompanyIdAndUserId(companyId, actorId))
        .thenReturn(
            Optional.of(
                new Membership(
                    UUID.randomUUID(), companyId, actorId, role, MembershipStatus.ACTIVE)));
  }
}
