package com.sanigest.company.application.usecase.profile;

import com.sanigest.company.application.dto.CompanyProfile;
import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.CompanyNotFoundException;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.domain.enums.MembershipStatus;
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
    var membership =
        memberships.findCompanyByUserId(userId).orElseThrow(CompanyNotFoundException::new);
    if (membership.status() != MembershipStatus.ACTIVE) {
      throw new CompanyAccessDeniedException();
    }
    return membership.company();
  }
}
