package com.sanigest.company.application.port.out.integration;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MemberIdentityDirectory {
  int MAX_BATCH = 100;

  Map<UUID, Identity> lookup(List<UUID> userIds);

  record Identity(String name, String email, boolean emailVerified) {}
}
