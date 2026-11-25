package com.sanigest.company.application.usecase.profile;

import com.sanigest.company.application.dto.UpdatedCompany;
import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.CompanyNotFoundException;
import com.sanigest.company.application.port.out.persistence.CompanyRepository;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import com.sanigest.company.domain.model.Company.ProfilePatch;
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
    var membership =
        memberships.findByUserIdForUpdate(userId).orElseThrow(CompanyNotFoundException::new);
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
