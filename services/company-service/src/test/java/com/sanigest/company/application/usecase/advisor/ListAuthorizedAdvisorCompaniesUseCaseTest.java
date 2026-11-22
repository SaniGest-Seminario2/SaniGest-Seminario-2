package com.sanigest.company.application.usecase.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.company.application.dto.AuthorizedAdvisorCompany;
import com.sanigest.company.application.exception.AuthorizationUnavailableException;
import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.port.out.integration.AdvisorEligibility;
import com.sanigest.company.application.port.out.persistence.AdvisorAccessRepository;
import com.sanigest.company.domain.model.AdvisorPermission;
import com.sanigest.company.domain.model.AdvisorPermission.Module;
import com.sanigest.company.domain.model.AdvisorPermission.Scope;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListAuthorizedAdvisorCompaniesUseCaseTest {
  private static final UUID ADVISOR = UUID.randomUUID();
  private static final AdvisorPermission READ = new AdvisorPermission(Module.CATALOG, Scope.READ);
  private static final AdvisorPermission WRITE = new AdvisorPermission(Module.SALES, Scope.WRITE);

  @Mock private AdvisorEligibility eligibility;
  @Mock private AdvisorAccessRepository access;

  static Stream<List<AuthorizedAdvisorCompany>> authorizedResults() {
    var first = new AuthorizedAdvisorCompany(UUID.randomUUID(), "Alpha", List.of(READ, WRITE));
    var second = new AuthorizedAdvisorCompany(UUID.randomUUID(), "Beta", List.of(READ));
    return Stream.of(List.of(), List.of(first), List.of(first, second));
  }

  @ParameterizedTest
  @MethodSource("authorizedResults")
  void returnsOnlyTheCurrentProjectionForTheVerifiedAdvisor(List<AuthorizedAdvisorCompany> result) {
    when(eligibility.isEligible(ADVISOR)).thenReturn(true);
    when(access.listAuthorizedCompanies(ADVISOR)).thenReturn(result);

    assertThat(new ListAuthorizedAdvisorCompaniesUseCase(eligibility, access).list(ADVISOR))
        .isEqualTo(result);
    var order = inOrder(eligibility, access);
    order.verify(eligibility).isEligible(ADVISOR);
    order.verify(access).listAuthorizedCompanies(ADVISOR);
  }

  @Test
  void rejectsAccountsThatIdentityReportsAsIneligible() {
    when(eligibility.isEligible(ADVISOR)).thenReturn(false);

    assertThatThrownBy(
            () -> new ListAuthorizedAdvisorCompaniesUseCase(eligibility, access).list(ADVISOR))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verifyNoInteractions(access);
  }

  @Test
  void doesNotQueryCompanyDataWhenIdentityCannotVerifyEligibility() {
    when(eligibility.isEligible(ADVISOR)).thenThrow(new AuthorizationUnavailableException());

    assertThatThrownBy(
            () -> new ListAuthorizedAdvisorCompaniesUseCase(eligibility, access).list(ADVISOR))
        .isInstanceOf(AuthorizationUnavailableException.class);
    verifyNoInteractions(access);
  }
}
