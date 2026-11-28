package com.sanigest.company.application.usecase.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.company.application.dto.MemberSearchCriteria;
import com.sanigest.company.application.exception.AuthorizationUnavailableException;
import com.sanigest.company.application.exception.MemberListLimitException;
import com.sanigest.company.application.port.out.integration.MemberIdentityDirectory;
import com.sanigest.company.application.port.out.integration.MemberIdentityDirectory.Identity;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.application.usecase.profile.GetMyCompanyUseCase;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import com.sanigest.company.domain.model.Membership;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemberPageUseCaseTest {
  private final UUID actor = UUID.randomUUID();
  private final UUID company = UUID.randomUUID();
  private final MembershipRepository memberships = mock(MembershipRepository.class);
  private final CompanyMembershipPolicy policy = mock(CompanyMembershipPolicy.class);
  private final MemberIdentityDirectory directory = mock(MemberIdentityDirectory.class);
  private final ListMyCompanyUsersUseCase useCase =
      new ListMyCompanyUsersUseCase(
          mock(GetMyCompanyUseCase.class), memberships, policy, directory);

  @Test
  void appliesFiltersAndOffsetInSqlBeforeEnrichingWithoutSearch() {
    var criteria =
        new MemberSearchCriteria(null, "ACTIVE", CompanyRole.ADMIN, 2, 3, "membershipId,desc");
    var member = member();
    when(policy.requireAdministrator(actor, company, false)).thenReturn(company);
    when(memberships.count(company, CompanyRole.ADMIN, Set.of(MembershipStatus.ACTIVE)))
        .thenReturn(7L);
    when(memberships.findBatch(
            company, CompanyRole.ADMIN, Set.of(MembershipStatus.ACTIVE), true, null, 3, 6))
        .thenReturn(List.of(member));
    when(directory.lookup(List.of(member.userId())))
        .thenReturn(Map.of(member.userId(), new Identity("María", "maria@example.com", false)));

    var page = useCase.page(actor, company, criteria);
    assertThat(page.totalElements()).isEqualTo(7);
    assertThat(page.totalPages()).isEqualTo(3);
    assertThat(page.items())
        .singleElement()
        .satisfies(item -> {
          assertThat(item.name()).isEqualTo("María");
          assertThat(item.membership()).isEqualTo(member);
        });
  }

  @Test
  void searchesNameAndEmailCaseInsensitivelyAndCountsMatches() {
    var criteria = new MemberSearchCriteria(" ACME ", null, null, 1, 1, "membershipId,asc");
    var first = member();
    var second = member();
    var other = member();
    when(policy.requireAdministrator(actor, company, false)).thenReturn(company);
    when(memberships.findBatch(company, null, Set.of(), false, null, 100, 0))
        .thenReturn(List.of(first, second, other));
    when(directory.lookup(List.of(first.userId(), second.userId(), other.userId())))
        .thenReturn(
            Map.of(
                first.userId(),
                new Identity("Acme User", "a@example.com", false),
                second.userId(),
                new Identity("Other", "user@acme.example", false),
                other.userId(),
                new Identity("External", "external@example.com", false)));
    var page = useCase.page(actor, company, criteria);
    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.items())
        .singleElement()
        .satisfies(item -> assertThat(item.membership()).isEqualTo(second));
  }

  @Test
  void doesNotReturnAnIncompleteResultIfIdentityOmitsARequestedUser() {
    var criteria = new MemberSearchCriteria("x", null, null, 0, 20, "membershipId,asc");
    var member = member();
    when(policy.requireAdministrator(actor, company, false)).thenReturn(company);
    when(memberships.findBatch(company, null, Set.of(), false, null, 100, 0))
        .thenReturn(List.of(member));
    assertThatThrownBy(() -> useCase.page(actor, company, criteria))
        .isInstanceOf(AuthorizationUnavailableException.class);
  }

  @Test
  void doesNotCallIdentityForEmptyPages() {
    var criteria = new MemberSearchCriteria(null, null, null, 0, 1, "membershipId,asc");
    when(policy.requireAdministrator(actor, company, false)).thenReturn(company);
    assertThat(useCase.page(actor, company, criteria).items()).isEmpty();
    verifyNoInteractions(directory);
  }

  @Test
  void rejectsUnpaginatedQueriesOverTheLimitBeforeLookingUpIdentities() {
    var criteria = new MemberSearchCriteria(null, "ALL", null, 0, 20, "membershipId,asc");
    when(policy.requireAdministrator(actor, company, false)).thenReturn(company);
    when(memberships.count(company, null, Set.of()))
        .thenReturn((long) ListMyCompanyUsersUseCase.ALL_LIMIT + 1);
    assertThatThrownBy(() -> useCase.all(actor, company, criteria))
        .isInstanceOf(MemberListLimitException.class);
    verifyNoInteractions(directory);
  }

  private Membership member() {
    return new Membership(
        UUID.randomUUID(), company, UUID.randomUUID(), CompanyRole.ADMIN, MembershipStatus.ACTIVE);
  }
}
