package com.sanigest.company.application.usecase.team;

import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.application.usecase.profile.GetMyCompanyUseCase;
import com.sanigest.company.domain.model.Membership;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListMyCompanyUsersUseCase {
  private final GetMyCompanyUseCase getMyCompany;
  private final MembershipRepository memberships;

  public ListMyCompanyUsersUseCase(
      GetMyCompanyUseCase getMyCompany, MembershipRepository memberships) {
    this.getMyCompany = getMyCompany;
    this.memberships = memberships;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<Membership> list(UUID userId) {
    return list(userId, null);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<Membership> list(UUID userId, UUID requestedCompany) {
    return memberships.findAllByCompanyId(
        (requestedCompany == null
                ? getMyCompany.get(userId)
                : getMyCompany.get(userId, requestedCompany))
            .companyId());
  }
}
