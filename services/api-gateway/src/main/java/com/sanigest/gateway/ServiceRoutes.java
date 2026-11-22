package com.sanigest.gateway;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class ServiceRoutes {
  @Bean
  HttpClientCustomizer disableTransportRetry() {
    return client -> client.disableRetry(true);
  }

  @Bean
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

  @Bean
  @ConditionalOnProperty("COMPANY_SERVICE_URL")
  RouteLocator companyRoutes(RouteLocatorBuilder builder, Environment environment) {
    return builder
        .routes()
        .route(
            "company",
            route ->
                route
                    .path("/api/v1/companies/**", "/api/v1/invitations/{token}/accept")
                    .uri(destination(environment, "COMPANY_SERVICE_URL")))
        .build();
  }

  @Bean
  @ConditionalOnProperty("CATALOG_SERVICE_URL")
  RouteLocator catalogRoutes(RouteLocatorBuilder builder, Environment environment) {
    return builder
        .routes()
        .route(
            "catalog",
            route ->
                route
                    .path("/api/v1/clients/**", "/api/v1/suppliers/**", "/api/v1/catalog-items/**")
                    .uri(destination(environment, "CATALOG_SERVICE_URL")))
        .build();
  }

  private URI destination(Environment environment, String property) {
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
