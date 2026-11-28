package com.sanigest.catalog.application.exception.client;

import org.springframework.security.access.AccessDeniedException;

public final class ClientListingForbiddenException extends AccessDeniedException {
  public ClientListingForbiddenException() {
    super("Client listing is not authorized");
  }
}
