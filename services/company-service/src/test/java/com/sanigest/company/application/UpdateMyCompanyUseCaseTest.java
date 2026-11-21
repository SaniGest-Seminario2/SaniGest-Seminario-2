package com.sanigest.company.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.company.application.GetMyCompanyUseCase.CompanyAccessDeniedException;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyNotFoundException;
import com.sanigest.company.application.GetMyCompanyUseCase.MembershipRepository;
import com.sanigest.company.domain.model.Company;
import com.sanigest.company.domain.model.Company.ProfilePatch;
import com.sanigest.company.domain.model.CompanyRole;
import com.sanigest.company.domain.model.Membership;
import com.sanigest.company.domain.model.MembershipStatus;
import com.sanigest.company.domain.port.CompanyRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UpdateMyCompanyUseCaseTest {
  private final UUID userId = UUID.randomUUID();
  private final Company original =
      new Company(
          UUID.randomUUID(),
          "Legal SRL",
          "Comercial",
          "123456789",
          "a@example.com",
          "8095555555",
          "Santo Domingo",
          "DOP",
          "ACTIVE");
  private final MembershipRepository memberships = mock(MembershipRepository.class);
  private final CompanyRepository companies = mock(CompanyRepository.class);
  private final UpdateMyCompanyUseCase useCase = new UpdateMyCompanyUseCase(memberships, companies);

  @Test
  void ownerCanClearRncAndUpdateAllContactFields() {
    membership(CompanyRole.OWNER, MembershipStatus.ACTIVE);
    var patch = new ProfilePatch("Nuevo", "b@example.com", "8095550000", "Santiago", true, null);
    var expected =
        new Company(
            original.id(),
            "Legal SRL",
            "Nuevo",
            null,
            "b@example.com",
            "8095550000",
            "Santiago",
            "DOP",
            "ACTIVE");
    assertUpdate(patch, expected);
  }

  @Test
  void adminUpdatesPartialProfileAndPreservesOmittedFields() {
    membership(CompanyRole.ADMIN, MembershipStatus.ACTIVE);
    var patch = new ProfilePatch(null, null, null, "Santiago", false, null);
    var expected =
        new Company(
            original.id(),
            original.name(),
            original.businessName(),
            original.rnc(),
            original.email(),
            original.phone(),
            "Santiago",
            original.currency(),
            original.status());
    assertUpdate(patch, expected);
  }

  @ParameterizedTest
  @CsvSource({
    "ADMIN, ACTIVE, true",
    "VIEWER, ACTIVE, false",
    "OPERATOR, ACTIVE, false",
    "OWNER, REVOKED, false",
    "ADMIN, SUSPENDED, false"
  })
  void rejectsInsufficientPermissions(
      CompanyRole role, MembershipStatus status, boolean changesRnc) {
    membership(role, status);
    var patch = new ProfilePatch(null, null, null, "Santiago", changesRnc, null);
    assertThatThrownBy(() -> useCase.update(userId, patch))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verifyNoInteractions(companies);
  }

  @Test
  void reportsMissingCompany() {
    var patch = new ProfilePatch(null, null, null, "Santiago", false, null);
    assertThatThrownBy(() -> useCase.update(userId, patch))
        .isInstanceOf(CompanyNotFoundException.class);
    verifyNoInteractions(companies);
    membership(CompanyRole.OWNER, MembershipStatus.ACTIVE);
    assertThatThrownBy(() -> useCase.update(userId, patch))
        .isInstanceOf(CompanyNotFoundException.class);
  }

  @Test
  void rejectsEmptyPatchAndInvalidDomainValues() {
    assertThatThrownBy(() -> new ProfilePatch(null, null, null, null, false, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ProfilePatch(" ", null, null, null, false, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ProfilePatch(null, null, "invalid", null, false, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ProfilePatch(null, null, null, null, true, "123"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void assertUpdate(ProfilePatch patch, Company expected) {
    var updatedAt = Instant.parse("2026-11-21T12:00:00Z");
    when(companies.findByIdForUpdate(original.id())).thenReturn(Optional.of(original));
    when(companies.updateProfile(expected)).thenReturn(updatedAt);
    assertThat(useCase.update(userId, patch))
        .isEqualTo(new UpdateMyCompanyUseCase.UpdatedCompany(expected, updatedAt));
  }

  private void membership(CompanyRole role, MembershipStatus status) {
    when(memberships.findByUserIdForUpdate(userId))
        .thenReturn(
            Optional.of(new Membership(UUID.randomUUID(), original.id(), userId, role, status)));
  }
}
