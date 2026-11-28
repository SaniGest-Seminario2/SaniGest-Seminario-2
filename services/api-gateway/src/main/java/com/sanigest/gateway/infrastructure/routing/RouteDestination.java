package com.sanigest.gateway.infrastructure.routing;

import java.net.URI;
import org.springframework.core.env.Environment;

public final class RouteDestination {
  private RouteDestination() {}

  public static URI destination(Environment environment, String property) {
    URI uri;
    try {
      uri = URI.create(environment.getRequiredProperty(property));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(property + " must be an HTTP(S) origin");
    }
    if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null
        || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
      throw new IllegalArgumentException(property + " must be an HTTP(S) origin");
    }
    return uri;
  }
}
