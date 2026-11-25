package com.sanigest.gateway.observability;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

@Configuration(proxyBeanMethods = false)
class InvitationObservationConfiguration {
  @Bean
  ObservationPredicate excludeInvitationTokenPaths() {
    return (name, context) ->
        !(context instanceof ServerRequestObservationContext request
            && request.getCarrier() != null
            && request
                .getCarrier()
                .getPath()
                .pathWithinApplication()
                .value()
                .startsWith("/api/v1/invitations/"));
  }
}
