package com.sanigest.company.application;

import com.sanigest.company.application.GetMyCompanyUseCase.CompanyAccessDeniedException;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyNotFoundException;
import com.sanigest.company.application.GetMyCompanyUseCase.MembershipRepository;
import com.sanigest.company.domain.model.Company;
import com.sanigest.company.domain.model.Company.ProfilePatch;
import com.sanigest.company.domain.model.CompanyRole;
import com.sanigest.company.domain.model.MembershipStatus;
import com.sanigest.company.domain.port.CompanyRepository;
import java.time.Instant;
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
    var updated = company.updateProfile(patch);
    return new UpdatedCompany(updated, companies.updateProfile(updated));
  }

  public record UpdatedCompany(Company company, Instant updatedAt) {}
}
