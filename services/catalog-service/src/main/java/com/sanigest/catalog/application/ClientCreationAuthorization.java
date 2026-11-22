package com.sanigest.catalog.application;

import java.util.Optional;
import java.util.UUID;

@FunctionalInterface
public interface ClientCreationAuthorization {
  /**
   * Resolves the tenant after Company verifies the actor's current membership and create-client
   * permission. An empty result denies access. An unavailable authority must throw {@link
   * ClientCreationException} with reason AUTHORIZATION_UNAVAILABLE.
   */
  Optional<UUID> authorizedCompany(UUID userId, String accessToken);
}
