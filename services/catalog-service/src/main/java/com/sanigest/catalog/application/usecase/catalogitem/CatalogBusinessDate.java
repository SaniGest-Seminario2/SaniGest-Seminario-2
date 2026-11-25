package com.sanigest.catalog.application.usecase.catalogitem;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class CatalogBusinessDate {
  private static final ZoneId ZONE = ZoneId.of("America/Santo_Domingo");
  private final Clock clock;

  public CatalogBusinessDate(Clock clock) {
    this.clock = clock.withZone(ZONE);
  }

  public LocalDate today() {
    return LocalDate.now(clock);
  }
}
