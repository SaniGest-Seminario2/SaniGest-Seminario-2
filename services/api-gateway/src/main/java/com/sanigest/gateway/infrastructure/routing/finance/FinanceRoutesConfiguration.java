package com.sanigest.gateway.infrastructure.routing.finance;

import static com.sanigest.gateway.infrastructure.routing.RouteDestination.destination;
import static org.springframework.cloud.gateway.support.RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;

@Configuration(proxyBeanMethods = false)
class FinanceRoutesConfiguration {
  @Bean
  @Order(7)
  @ConditionalOnProperty("FINANCE_SERVICE_URL")
  RouteLocator financeRoutes(RouteLocatorBuilder builder, Environment environment) {
    return builder
        .routes()
        .route(
            "finance",
            route ->
                route
                    .path(
                        "/api/v1/cash-sessions/active/summary",
                        "/api/v1/finance/audit-log",
                        "/api/v1/finance/options",
                        "/api/v1/cash-movements/{id}/attachment")
                    .and()
                    .method(HttpMethod.GET)
                    .uri(destination(environment, "FINANCE_SERVICE_URL")))
        .route(
            "finance-movement-write",
            route ->
                route
                    .path("/api/v1/cash-movements")
                    .and()
                    .method(HttpMethod.POST)
                    .uri(destination(environment, "FINANCE_SERVICE_URL")))
        .route(
            "finance-projection",
            route ->
                route
                    .path("/api/v1/dashboard/projection", "/api/v1/reports/projection")
                    .and()
                    .method(HttpMethod.GET)
                    .metadata(RESPONSE_TIMEOUT_ATTR, 15_000)
                    .uri(destination(environment, "FINANCE_SERVICE_URL")))
        .route(
            "finance-projection-rebuild",
            route ->
                route
                    .path("/api/v1/reports/projection/rebuild")
                    .and()
                    .method(HttpMethod.POST)
                    .metadata(RESPONSE_TIMEOUT_ATTR, 15_000)
                    .uri(destination(environment, "FINANCE_SERVICE_URL")))
        .build();
  }
}
