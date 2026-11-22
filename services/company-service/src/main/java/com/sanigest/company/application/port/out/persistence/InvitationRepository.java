package com.sanigest.company.application.port.out.persistence;

import com.sanigest.company.domain.enums.InvitationStatus;
import com.sanigest.company.domain.model.Invitation;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository {
  Optional<Invitation> findPendingForUpdate(UUID companyId, String email);

  Optional<Invitation> findByTokenHashForUpdate(String tokenHash);

  void insert(Invitation invitation, String tokenHash);

  void closePending(UUID id, InvitationStatus status);
}
