package com.sanigest.gateway.routing;

import static org.springframework.cloud.gateway.support.RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;

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
                    .path(
                        "/api/v1/companies",
                        "/api/v1/companies/{companyId}",
                        "/api/v1/companies/{companyId}/options",
                        "/api/v1/companies/{companyId}/users",
                        "/api/v1/companies/{companyId}/invitations",
                        "/api/v1/companies/{companyId}/invitations/{invitationId}",
                        "/api/v1/companies/{companyId}/members/{userId}",
                        "/api/v1/companies/{companyId}/members/{userId}/role",
                        "/api/v1/companies/{companyId}/members/{userId}/status",
                        "/api/v1/companies/{companyId}/audit-log",
                        "/api/v1/companies/{companyId}/advisor-access",
                        "/api/v1/invitations/{token}/accept",
                        "/api/v1/advisor/companies")
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
                    .path(
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

  @Bean
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

  @Bean
  @ConditionalOnProperty("INVENTORY_SERVICE_URL")
  RouteLocator inventoryRoutes(RouteLocatorBuilder builder, Environment environment) {
    return builder
        .routes()
        .route(
            "inventory",
            route ->
                route
                    .path(
                        "/api/v1/warehouses",
                        "/api/v1/warehouses/{id}",
                        "/api/v1/inventory/options",
                        "/api/v1/inventory/stock",
                        "/api/v1/inventory/stock/{itemId}/{warehouseId}",
                        "/api/v1/inventory/summary",
                        "/api/v1/inventory/movements",
                        "/api/v1/inventory/adjustments")
                    .uri(destination(environment, "INVENTORY_SERVICE_URL")))
        .build();
  }

  @Bean
  @ConditionalOnProperty("EXPENSE_SERVICE_URL")
  RouteLocator expenseRoutes(RouteLocatorBuilder builder, Environment environment) {
    return builder
        .routes()
        .route(
            "expense",
            route ->
                route
                    .path("/api/v1/expenses")
                    .and()
                    .method(HttpMethod.POST)
                    .metadata(RESPONSE_TIMEOUT_ATTR, 15_000)
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

  @Bean
  @ConditionalOnProperty("FINANCE_SERVICE_URL")
  RouteLocator financeRoutes(RouteLocatorBuilder builder, Environment environment) {
    return builder
        .routes()
        .route(
            "finance",
            route ->
                route
                    .path("/api/v1/cash-sessions/active/summary")
                    .and()
                    .method(HttpMethod.GET)
                    .uri(destination(environment, "FINANCE_SERVICE_URL")))
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
