package com.sanigest.company.application.port.out.persistence;

import com.sanigest.company.domain.enums.InvitationStatus;
import com.sanigest.company.domain.model.Invitation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository {
  Optional<Invitation> findPendingForUpdate(UUID companyId, String email);

  Optional<Invitation> findByTokenHashForUpdate(String tokenHash);

  Optional<UUID> findCompanyByTokenHash(String tokenHash);

  Optional<Invitation> findByCompanyAndIdForUpdate(UUID companyId, UUID id);

  List<Invitation> findPending(UUID companyId, Instant now, int limit, long offset);

  void insert(Invitation invitation, String tokenHash);

  void closePending(UUID id, InvitationStatus status);
}
