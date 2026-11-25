package com.sanigest.company.api.routing;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

@Configuration(proxyBeanMethods = false)
class InvitationObservationConfiguration {
  @Bean
  ObservationPredicate excludeInvitationTokenPaths() {
    return (name, context) ->
        !(context instanceof ServerRequestObservationContext request
            && request.getCarrier() != null
            && CompanyRoutes.isInvitationPath(request.getCarrier().getRequestURI()));
  }
}
