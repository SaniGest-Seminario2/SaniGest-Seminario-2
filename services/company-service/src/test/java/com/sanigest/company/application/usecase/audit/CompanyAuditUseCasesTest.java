package com.sanigest.company.application.usecase.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.company.application.dto.CompanyMembership;
import com.sanigest.company.application.dto.CompanyProfile;
import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.port.out.persistence.CompanyAuditRepository;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.domain.enums.AuditOperation;
import com.sanigest.company.domain.enums.AuditResult;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import com.sanigest.company.domain.model.CompanyAuditEvent;
import com.sanigest.company.domain.model.Membership;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class CompanyAuditUseCasesTest {
  private static final Instant NOW = Instant.parse("2026-04-01T11:12:13Z");
  private final UUID company = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID target = UUID.randomUUID();
  private final MembershipRepository memberships = mock(MembershipRepository.class);
  private final CompanyAuditRepository repository = mock(CompanyAuditRepository.class);
  private final GetCompanyAuditLogUseCase useCase =
      new GetCompanyAuditLogUseCase(memberships, repository);

  @Test
  void recordsOnlyTypedMetadataWithServerGeneratedIdAndTime() {
    var recorder = new CompanyAuditRecorder(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    recorder.succeeded(
        actor,
        new Membership(
            UUID.randomUUID(), company, target, CompanyRole.VIEWER, MembershipStatus.ACTIVE),
        AuditOperation.ROLE_CHANGED,
        CompanyRole.VIEWER,
        CompanyRole.ADMIN,
        null,
        null);
    recorder.rejected(company, actor, null, AuditOperation.MEMBER_REVOKED);

    var captor = ArgumentCaptor.forClass(CompanyAuditEvent.class);
    verify(repository, org.mockito.Mockito.times(2)).append(captor.capture());
    var success = captor.getAllValues().get(0);
    var rejection = captor.getAllValues().get(1);
    assertThat(success.id()).isNotNull().isNotEqualTo(rejection.id());
    assertThat(success.correlationId()).matches("[0-9a-f]{32}");
    assertThat(rejection.correlationId()).matches("[0-9a-f]{32}");
    assertThat(success)
        .isEqualTo(
            new CompanyAuditEvent(
                success.id(),
                company,
                actor,
                target,
                AuditOperation.ROLE_CHANGED,
                NOW,
                AuditResult.SUCCESS,
                CompanyRole.VIEWER,
                CompanyRole.ADMIN,
                null,
                null,
                null,
                success.correlationId()));
    assertThat(rejection)
        .isEqualTo(
            new CompanyAuditEvent(
                rejection.id(),
                company,
                actor,
                null,
                AuditOperation.MEMBER_REVOKED,
                NOW,
                AuditResult.REJECTED,
                null,
                null,
                null,
                null,
                null,
                rejection.correlationId()));
  }

  @ParameterizedTest
  @CsvSource({"OWNER, false", "VIEWER, true"})
  void ownerAndExplicitAuditorCanReadUsingOnlyTheResolvedCompany(
      CompanyRole role, boolean granted) {
    var member = stubMembership(role, MembershipStatus.ACTIVE);
    when(memberships.hasPermission(member, GetCompanyAuditLogUseCase.AUDIT_LOG_READ))
        .thenReturn(granted);
    var event =
        new CompanyAuditEvent(
            UUID.randomUUID(),
            company,
            actor,
            target,
            AuditOperation.MEMBER_REVOKED,
            NOW,
            AuditResult.SUCCESS,
            null,
            null,
            MembershipStatus.ACTIVE,
            MembershipStatus.REVOKED,
            null,
            null);
    when(repository.findPage(company, NOW, NOW, 2, 2)).thenReturn(List.of(event, event));

    var page = useCase.get(actor, NOW, NOW, 2, 1);
    assertThat(page.content()).containsExactly(event);
    assertThat(page.hasNext()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"ADMIN, ACTIVE", "OPERATOR, ACTIVE", "VIEWER, ACTIVE", "OWNER, REVOKED"})
  void deniesMembersWithoutCurrentAuthorization(CompanyRole role, MembershipStatus status) {
    stubMembership(role, status);
    assertThatThrownBy(() -> useCase.get(actor, null, null, 0, 20))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void validatesDateRangeAndPagination() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> useCase.get(actor, NOW, NOW.minusSeconds(1), 0, 20));
    assertThatIllegalArgumentException().isThrownBy(() -> useCase.get(actor, null, null, -1, 20));
    assertThatIllegalArgumentException().isThrownBy(() -> useCase.get(actor, null, null, 0, 0));
    assertThatIllegalArgumentException().isThrownBy(() -> useCase.get(actor, null, null, 0, 101));
    verifyNoInteractions(repository);
  }

  private Membership stubMembership(CompanyRole role, MembershipStatus status) {
    var profile =
        new CompanyProfile(company, "Legal", "Business", null, null, "DOP", "ACTIVE", null, null);
    var membership = new Membership(UUID.randomUUID(), company, actor, role, status);
    when(memberships.findCompanyByUserId(actor))
        .thenReturn(Optional.of(new CompanyMembership(status, profile)));
    when(memberships.findByCompanyIdAndUserId(company, actor)).thenReturn(Optional.of(membership));
    return membership;
  }
}
