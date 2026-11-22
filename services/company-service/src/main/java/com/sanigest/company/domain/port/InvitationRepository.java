package com.sanigest.company.domain.port;

import com.sanigest.company.domain.model.Invitation;
import com.sanigest.company.domain.model.Invitation.Status;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository {
  Optional<Invitation> findPendingForUpdate(UUID companyId, String email);

  Optional<Invitation> findByTokenHashForUpdate(String tokenHash);

  void insert(Invitation invitation, String tokenHash);

  void closePending(UUID id, Status status);
}
