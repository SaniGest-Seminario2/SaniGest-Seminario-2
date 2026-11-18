package com.sanigest.company.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanigest.company.domain.model.Company;
import com.sanigest.company.domain.model.CompanyRole;
import com.sanigest.company.domain.model.Membership;
import com.sanigest.company.domain.model.MembershipStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanyDomainTest {
  @Test
  void rejectsBlankCompanyNames() {
    assertThatThrownBy(() -> new Company(UUID.randomUUID(), " ", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsValidCompanyAndMembershipUsingExternalUserIdOnly() {
    UUID companyId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    Company company = new Company(companyId, "Clínica Central", "123456789");
    Membership membership =
        new Membership(
            UUID.randomUUID(), companyId, userId, CompanyRole.OWNER, MembershipStatus.ACTIVE);

    org.assertj.core.api.Assertions.assertThat(company.name()).isEqualTo("Clínica Central");
    org.assertj.core.api.Assertions.assertThat(membership.userId()).isEqualTo(userId);
    org.assertj.core.api.Assertions.assertThat(membership.role()).isEqualTo(CompanyRole.OWNER);
  }

  @Test
  void rejectsMembershipMissingAnyRequiredIdentityOrAuthorizationField() {
    assertThatThrownBy(() ->
            new Membership(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                MembershipStatus.ACTIVE))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
