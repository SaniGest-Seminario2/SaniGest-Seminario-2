package com.sanigest.catalog.application;

import static com.sanigest.catalog.application.CatalogAuthorization.Permission.CATALOG_READ;
import static com.sanigest.catalog.application.ClientCreationException.Reason.AUTHORIZATION_UNAVAILABLE;

import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ListClients {
  public static final String DEFAULT_SIZE = "20";

  private final Optional<CatalogAuthorization> authorization;
  private final ClientRepository clients;
  private final int maxSize;

  public ListClients(
      Optional<CatalogAuthorization> authorization,
      ClientRepository clients,
      @Value("${sanigest.catalog.clients.max-page-size:100}") int maxSize) {
    if (maxSize < Integer.parseInt(DEFAULT_SIZE)) {
      throw new IllegalArgumentException("The maximum client page size must allow the default");
    }
    this.authorization = authorization;
    this.clients = clients;
    this.maxSize = maxSize;
  }

  @PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
  public ClientPage execute(String search, String status, int page, int size) {
    if (page < 0 || size < 1 || size > maxSize) {
      throw new InvalidQuery();
    }
    var term = optionalFilter(search);
    if (term != null && term.indexOf(0) >= 0) {
      throw new InvalidQuery();
    }
    var state = optionalFilter(status);
    if (state != null) {
      state = state.toUpperCase(Locale.ROOT);
      if (!"ACTIVE".equals(state) && !"INACTIVE".equals(state)) {
        throw new InvalidQuery();
      }
    }
    var companyId =
        authorization
            .orElseThrow(() -> new ClientCreationException(AUTHORIZATION_UNAVAILABLE))
            .membership(CATALOG_READ)
            .orElseThrow(Forbidden::new);
    return clients.search(companyId, term, state, page, size);
  }

  private static String optionalFilter(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  public static final class Forbidden extends AccessDeniedException {
    public Forbidden() {
      super("Client listing is not authorized");
    }
  }

  public static final class InvalidQuery extends RuntimeException {}
}
