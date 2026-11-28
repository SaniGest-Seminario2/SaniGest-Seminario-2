package com.sanigest.gateway.infrastructure.routing.expense;

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
class ExpenseRoutesConfiguration {
  @Bean
  @Order(6)
  @ConditionalOnProperty("EXPENSE_SERVICE_URL")
  RouteLocator expenseRoutes(RouteLocatorBuilder builder, Environment environment) {
    return builder
        .routes()
        .route(
            "expense",
            route ->
                route
                    .path(
                        "/api/v1/expenses",
                        "/api/v1/expenses/{id}/cancel",
                        "/api/v1/payables/{id}/payments")
                    .and()
                    .method(HttpMethod.POST)
                    .metadata(RESPONSE_TIMEOUT_ATTR, 15_000)
                    .uri(destination(environment, "EXPENSE_SERVICE_URL")))
        .route(
            "expense-read",
            route ->
                route
                    .path("/api/v1/expenses", "/api/v1/expenses/*")
                    .and()
                    .method(HttpMethod.GET)
                    .uri(destination(environment, "EXPENSE_SERVICE_URL")))
        .route(
            "payables",
            route ->
                route
                    .path(
                        "/api/v1/payables",
                        "/api/v1/payables/all",
                        "/api/v1/payables/supplier-balances")
                    .and()
                    .method(HttpMethod.GET)
                    .uri(destination(environment, "EXPENSE_SERVICE_URL")))
        .build();
  }
}
