package com.sanigest.company.application.usecase.access;

import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.application.port.out.persistence.MembershipRepository.ActiveAccess;
import com.sanigest.company.application.usecase.advisor.CheckAdvisorPermissionUseCase;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.model.AdvisorPermission;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ResolveCompanyAccess {
  public static final String AUDIT_LOG_READ = "AUDIT_LOG_READ";

  private final MembershipRepository memberships;
  private final CheckAdvisorPermissionUseCase advisors;

  public ResolveCompanyAccess(
      MembershipRepository memberships, CheckAdvisorPermissionUseCase advisors) {
    this.memberships = memberships;
    this.advisors = advisors;
  }

  public ActiveAccess membership(UUID userId, UUID companyId, String permission) {
    return memberships
        .findActiveAccess(userId, companyId, permission)
        .orElseThrow(CompanyAccessDeniedException::new);
  }

  public ActiveAccess auditMembership(UUID userId, UUID companyId) {
    var access = membership(userId, companyId, AUDIT_LOG_READ);
    if (access.role() != CompanyRole.OWNER && !access.explicitGrant()) {
      throw new CompanyAccessDeniedException();
    }
    return access;
  }

  public void requireDelegation(UUID userId, UUID companyId, AdvisorPermission permission) {
    advisors.require(userId, companyId, permission);
  }
}
