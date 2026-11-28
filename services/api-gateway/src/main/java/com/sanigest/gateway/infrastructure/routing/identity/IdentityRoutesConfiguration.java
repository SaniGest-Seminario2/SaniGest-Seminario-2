package com.sanigest.gateway.infrastructure.routing.identity;

import static com.sanigest.gateway.infrastructure.routing.RouteDestination.destination;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class IdentityRoutesConfiguration {
  @Bean
  @Order(1)
  @ConditionalOnProperty("IDENTITY_SERVICE_URL")
  RouteLocator identityRoutes(RouteLocatorBuilder builder, Environment environment) {
    return builder
        .routes()
        .route(
            "identity",
            route ->
                route
                    .path("/api/v1/auth/**", "/api/v1/users/**")
                    .uri(destination(environment, "IDENTITY_SERVICE_URL")))
        .build();
  }
}
