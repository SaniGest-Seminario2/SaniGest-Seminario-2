package com.sanigest.company.application.usecase.profile;

import com.sanigest.company.application.dto.profile.CompanyProfile;
import com.sanigest.company.application.exception.access.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.profile.CompanyNotFoundException;
import com.sanigest.company.application.port.out.persistence.team.MembershipRepository;
import com.sanigest.company.application.usecase.access.OperationalCompanySelection;
import com.sanigest.company.domain.enums.team.MembershipStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetMyCompanyUseCase {
  private final MembershipRepository memberships;

  public GetMyCompanyUseCase(MembershipRepository memberships) {
    this.memberships = memberships;
  }

  @Transactional(readOnly = true)
  public CompanyProfile get(UUID userId) {
    return get(userId, null);
  }

  @Transactional(readOnly = true)
  public CompanyProfile get(UUID userId, UUID requestedCompany) {
    var membership =
        OperationalCompanySelection.profile(memberships, userId, requestedCompany)
            .orElseThrow(CompanyNotFoundException::new);
    if (membership.status() != MembershipStatus.ACTIVE) {
      throw new CompanyAccessDeniedException();
    }
    return membership.company();
  }
}
