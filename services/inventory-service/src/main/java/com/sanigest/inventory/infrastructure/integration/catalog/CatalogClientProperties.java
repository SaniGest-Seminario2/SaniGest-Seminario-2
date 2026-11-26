package com.sanigest.inventory.infrastructure.integration.catalog;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sanigest.catalog")
record CatalogClientProperties(URI origin, Duration connectTimeout, Duration responseTimeout) {
  CatalogClientProperties {
    requirePositive(connectTimeout);
    requirePositive(responseTimeout);
    if (origin != null && origin.toString().isEmpty()) {
      origin = null;
    }
  }

  private static void requirePositive(Duration timeout) {
    if (timeout == null
        || timeout.compareTo(Duration.ofMillis(1)) < 0
        || timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
      throw new IllegalArgumentException("Catalog timeout is outside the supported range");
    }
  }

  void validateOrigin(boolean allowHttp) {
    if (origin == null) {
      return;
    }
    boolean supportedScheme =
        "https".equalsIgnoreCase(origin.getScheme())
            || (allowHttp && "http".equalsIgnoreCase(origin.getScheme()));
    if (!supportedScheme
        || origin.getHost() == null
        || origin.getUserInfo() != null
        || origin.getRawQuery() != null
        || origin.getRawFragment() != null
        || (!origin.getRawPath().isEmpty() && !origin.getRawPath().equals("/"))
        || origin.getPort() == 0
        || origin.getPort() > 65535) {
      throw new IllegalArgumentException("Catalog requires a dedicated HTTP(S) origin");
    }
  }
}
