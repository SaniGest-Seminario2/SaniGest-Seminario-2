package com.sanigest.company.application.usecase.advisor;

import com.sanigest.company.application.dto.advisor.AuthorizedAdvisorCompany;
import com.sanigest.company.application.exception.access.CompanyAccessDeniedException;
import com.sanigest.company.application.port.out.integration.advisor.AdvisorEligibility;
import com.sanigest.company.application.port.out.persistence.advisor.AdvisorAccessRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ListAuthorizedAdvisorCompaniesUseCase {
  private final AdvisorEligibility eligibility;
  private final AdvisorAccessRepository access;

  ListAuthorizedAdvisorCompaniesUseCase(
      AdvisorEligibility eligibility, AdvisorAccessRepository access) {
    this.eligibility = eligibility;
    this.access = access;
  }

  public List<AuthorizedAdvisorCompany> list(UUID advisorId) {
    if (!eligibility.isEligible(advisorId)) {
      throw new CompanyAccessDeniedException();
    }
    return access.listAuthorizedCompanies(advisorId);
  }
}
