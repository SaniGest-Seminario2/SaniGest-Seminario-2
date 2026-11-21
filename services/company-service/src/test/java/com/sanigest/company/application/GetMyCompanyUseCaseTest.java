package com.sanigest.company.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sanigest.company.application.GetMyCompanyUseCase.CompanyAccessDeniedException;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyMembership;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyNotFoundException;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyProfile;
import com.sanigest.company.application.GetMyCompanyUseCase.MembershipRepository;
import com.sanigest.company.domain.model.MembershipStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GetMyCompanyUseCaseTest {
  private final UUID userId = UUID.randomUUID();
  private final MembershipRepository memberships = mock(MembershipRepository.class);
  private final GetMyCompanyUseCase useCase = new GetMyCompanyUseCase(memberships);
  private final CompanyProfile profile =
      new CompanyProfile(UUID.randomUUID(), "Clinic SRL", "Clinic", null, "SD", "DOP", "ACTIVE");

  @Test
  void resolvesTheCompanyThroughTheAuthenticatedUsersMembership() {
    when(memberships.findCompanyByUserId(userId))
        .thenReturn(Optional.of(new CompanyMembership(MembershipStatus.ACTIVE, profile)));

    assertThat(useCase.get(userId)).isEqualTo(profile);
    verify(memberships).findCompanyByUserId(userId);
  }

  @Test
  void reportsNoCompanyWhenTheUserHasNoMembership() {
    when(memberships.findCompanyByUserId(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.get(userId)).isInstanceOf(CompanyNotFoundException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = MembershipStatus.class,
      names = {"SUSPENDED", "REVOKED"})
  void deniesInactiveMemberships(MembershipStatus status) {
    when(memberships.findCompanyByUserId(userId))
        .thenReturn(Optional.of(new CompanyMembership(status, profile)));

    assertThatThrownBy(() -> useCase.get(userId)).isInstanceOf(CompanyAccessDeniedException.class);
  }
}
