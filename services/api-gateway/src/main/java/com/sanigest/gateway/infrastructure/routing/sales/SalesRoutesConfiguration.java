package com.sanigest.gateway.infrastructure.routing.sales;

import static com.sanigest.gateway.infrastructure.routing.RouteDestination.destination;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class SalesRoutesConfiguration {
  @Bean
  @Order(4)
  @ConditionalOnProperty("SALES_SERVICE_URL")
  RouteLocator salesRoutes(RouteLocatorBuilder builder, Environment environment) {
    return builder
        .routes()
        .route(
            "sales",
            route ->
                route
                    .path(
                        "/api/v1/sales",
                        "/api/v1/sales/audit-log",
                        "/api/v1/sales/{id}",
                        "/api/v1/sales/{id}/cancel",
                        "/api/v1/sales/{id}/invoices",
                        "/api/v1/invoices",
                        "/api/v1/invoices/all",
                        "/api/v1/invoices/collectible",
                        "/api/v1/invoices/collectible/all",
                        "/api/v1/invoices/{id}",
                        "/api/v1/invoices/{id}/cancel",
                        "/api/v1/invoices/{id}/payments",
                        "/api/v1/receivables",
                        "/api/v1/receivables/all",
                        "/api/v1/receivables/{id}",
                        "/api/v1/cash-sessions/active/payments")
                    .uri(destination(environment, "SALES_SERVICE_URL")))
        .build();
  }
}
