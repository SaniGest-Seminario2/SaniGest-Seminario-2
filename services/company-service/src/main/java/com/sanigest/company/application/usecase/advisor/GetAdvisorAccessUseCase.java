package com.sanigest.company.application.usecase.advisor;

import com.sanigest.company.application.port.out.persistence.advisor.AdvisorAccessRepository;
import com.sanigest.company.domain.model.advisor.AdvisorAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetAdvisorAccessUseCase {
  private final AdvisorOwnerPolicy owners;
  private final AdvisorAccessRepository access;

  GetAdvisorAccessUseCase(AdvisorOwnerPolicy owners, AdvisorAccessRepository access) {
    this.owners = owners;
    this.access = access;
  }

  @Transactional
  public List<AdvisorAccess> get(UUID actorId) {
    return get(actorId, null);
  }

  @Transactional
  public List<AdvisorAccess> get(UUID actorId, UUID requestedCompany) {
    UUID companyId = owners.requireOwner(actorId, requestedCompany);
    owners.lockAndRecheck(actorId, companyId);
    return access.list(companyId);
  }
}
