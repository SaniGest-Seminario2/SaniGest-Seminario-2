package com.sanigest.company.application.usecase.profile;

import com.sanigest.company.application.dto.profile.UpdatedCompany;
import com.sanigest.company.application.exception.access.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.profile.CompanyNotFoundException;
import com.sanigest.company.application.port.out.persistence.profile.CompanyRepository;
import com.sanigest.company.application.port.out.persistence.team.MembershipRepository;
import com.sanigest.company.application.usecase.access.OperationalCompanySelection;
import com.sanigest.company.domain.enums.team.CompanyRole;
import com.sanigest.company.domain.enums.team.MembershipStatus;
import com.sanigest.company.domain.model.profile.Company.ProfilePatch;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateMyCompanyUseCase {
  private final MembershipRepository memberships;
  private final CompanyRepository companies;

  public UpdateMyCompanyUseCase(MembershipRepository memberships, CompanyRepository companies) {
    this.memberships = memberships;
    this.companies = companies;
  }

  @Transactional
  public UpdatedCompany update(UUID userId, ProfilePatch patch) {
    return update(userId, null, patch);
  }

  @Transactional
  public UpdatedCompany update(UUID userId, UUID requestedCompany, ProfilePatch patch) {
    var membership =
        OperationalCompanySelection.lockMembership(memberships, userId, requestedCompany)
            .orElseThrow(CompanyNotFoundException::new);
    var role = membership.role();
    if (membership.status() != MembershipStatus.ACTIVE
        || (role != CompanyRole.OWNER && role != CompanyRole.ADMIN)
        || (patch.rncProvided() && role != CompanyRole.OWNER)) {
      throw new CompanyAccessDeniedException();
    }

    var company =
        companies
            .findByIdForUpdate(membership.companyId())
            .orElseThrow(CompanyNotFoundException::new);
    if (!"ACTIVE".equals(company.status())) {
      throw new CompanyAccessDeniedException();
    }
    var updated = company.updateProfile(patch);
    return new UpdatedCompany(updated, companies.updateProfile(updated));
  }
}
