package com.sanigest.company.application.usecase.advisor;

import com.sanigest.company.application.port.out.persistence.AdvisorAccessRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevokeAdvisorAccessUseCase {
  private final AdvisorOwnerPolicy owners;
  private final AdvisorAccessRepository access;
  private final AdvisorAccessChanges changes;

  RevokeAdvisorAccessUseCase(
      AdvisorOwnerPolicy owners, AdvisorAccessRepository access, AdvisorAccessChanges changes) {
    this.owners = owners;
    this.access = access;
    this.changes = changes;
  }

  @Transactional
  public void revoke(UUID actorId, UUID advisorId) {
    UUID companyId = owners.requireOwner(actorId);
    owners.lockAndRecheck(actorId, companyId);
    var current = access.find(companyId, advisorId);
    // Keep a revision even when DELETE wins a race against an initial PUT.
    if (current.revision() == 0 || !current.permissions().isEmpty()) {
      changes.replace(companyId, actorId, current, List.of());
    }
  }
}
