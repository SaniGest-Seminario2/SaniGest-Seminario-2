package com.sanigest.gateway.infrastructure.routing.catalog;

import static com.sanigest.gateway.infrastructure.routing.RouteDestination.destination;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class CatalogRoutesConfiguration {
  @Bean
  @Order(3)
  @ConditionalOnProperty("CATALOG_SERVICE_URL")
  RouteLocator catalogRoutes(RouteLocatorBuilder builder, Environment environment) {
    return builder
        .routes()
        .route(
            "catalog",
            route ->
                route
                    .path(
                        "/api/v1/catalog/audit-log",
                        "/api/v1/catalog/options",
                        "/api/v1/clients",
                        "/api/v1/clients/all",
                        "/api/v1/clients/summary",
                        "/api/v1/clients/{id}/financial-summary",
                        "/api/v1/clients/{id}/statement",
                        "/api/v1/clients/{id}",
                        "/api/v1/clients/{id}/status",
                        "/api/v1/clients/categories/{id}",
                        "/api/v1/clients/reference-data/{resource}",
                        "/api/v1/suppliers",
                        "/api/v1/suppliers/{id}",
                        "/api/v1/suppliers/{id}/status",
                        "/api/v1/suppliers/{id}/items",
                        "/api/v1/catalog-items",
                        "/api/v1/catalog-items/{id}",
                        "/api/v1/catalog-items/{id}/status",
                        "/api/v1/catalog-items/{id}/suppliers",
                        "/api/v1/catalog-items/{id}/history",
                        "/api/v1/catalog-items/categories/{id}")
                    .uri(destination(environment, "CATALOG_SERVICE_URL")))
        .build();
  }
}
