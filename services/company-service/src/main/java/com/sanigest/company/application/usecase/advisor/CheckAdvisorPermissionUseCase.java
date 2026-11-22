package com.sanigest.company.application.usecase.advisor;

import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.port.out.integration.AdvisorEligibility;
import com.sanigest.company.application.port.out.persistence.AdvisorAccessRepository;
import com.sanigest.company.domain.model.AdvisorPermission;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CheckAdvisorPermissionUseCase {
  private final AdvisorEligibility eligibility;
  private final AdvisorAccessRepository access;

  CheckAdvisorPermissionUseCase(AdvisorEligibility eligibility, AdvisorAccessRepository access) {
    this.eligibility = eligibility;
    this.access = access;
  }

  /** The caller supplies the authenticated advisor and the tenant of the resource. */
  public void require(UUID advisorId, UUID companyId, AdvisorPermission permission) {
    if (!eligibility.isEligible(advisorId) || !access.allows(companyId, advisorId, permission)) {
      throw new CompanyAccessDeniedException();
    }
  }
}
