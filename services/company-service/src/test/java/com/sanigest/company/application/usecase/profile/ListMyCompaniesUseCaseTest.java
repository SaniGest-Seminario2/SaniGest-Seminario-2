package com.sanigest.company.application.usecase.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sanigest.company.application.dto.access.AuthorizedCompany;
import com.sanigest.company.application.port.out.persistence.team.MembershipRepository;
import com.sanigest.company.domain.enums.team.CompanyRole;
import com.sanigest.company.domain.enums.team.MembershipStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListMyCompaniesUseCaseTest {
  @Test
  void preservesTheOrderedProjectionForTheAuthenticatedUser() {
    var user = UUID.randomUUID();
    var memberships = mock(MembershipRepository.class);
    var useCase = new ListMyCompaniesUseCase(memberships);
    var first =
        new AuthorizedCompany(
            UUID.randomUUID(), "A SRL", "A", CompanyRole.OWNER, MembershipStatus.ACTIVE, "ACTIVE");
    var second =
        new AuthorizedCompany(
            UUID.randomUUID(), "B SRL", "B", CompanyRole.VIEWER, MembershipStatus.ACTIVE, "ACTIVE");

    when(memberships.findAuthorizedCompanies(user))
        .thenReturn(List.of())
        .thenReturn(List.of(first))
        .thenReturn(List.of(first, second));
    assertThat(useCase.list(user)).isEmpty();
    assertThat(useCase.list(user)).containsExactly(first);
    assertThat(useCase.list(user)).containsExactly(first, second).doesNotHaveDuplicates();
  }
}
