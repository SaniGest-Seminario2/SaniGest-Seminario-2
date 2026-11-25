package com.sanigest.catalog.infrastructure.configuration;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CatalogClockConfiguration {
  @Bean
  Clock catalogClock() {
    return Clock.systemUTC();
  }
}
