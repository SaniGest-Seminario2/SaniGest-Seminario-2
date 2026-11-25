package com.sanigest.company.application.usecase.profile;

import com.sanigest.company.application.dto.AuthorizedCompany;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ListMyCompaniesUseCase {
  private final MembershipRepository memberships;

  public ListMyCompaniesUseCase(MembershipRepository memberships) {
    this.memberships = memberships;
  }

  public List<AuthorizedCompany> list(UUID userId) {
    return memberships.findAuthorizedCompanies(userId);
  }
}
