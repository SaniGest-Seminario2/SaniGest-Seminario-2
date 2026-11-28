package com.sanigest.gateway.api.observability;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

@Configuration(proxyBeanMethods = false)
class InvitationObservationConfiguration {
  private static final PathPattern INVITATIONS =
      PathPatternParser.defaultInstance.parse("/api/v1/invitations/**");

  @Bean
  ObservationPredicate excludeInvitationTokenPaths() {
    return (name, context) ->
        !(context instanceof ServerRequestObservationContext request
            && request.getCarrier() != null
            && INVITATIONS.matches(request.getCarrier().getPath().pathWithinApplication()));
  }
}
