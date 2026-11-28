package com.sanigest.gateway.infrastructure.routing.inventory;

import static com.sanigest.gateway.infrastructure.routing.RouteDestination.destination;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class InventoryRoutesConfiguration {
  @Bean
  @Order(5)
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
                        "/api/v1/inventory/audit-log",
                        "/api/v1/inventory/options",
                        "/api/v1/inventory/stock",
                        "/api/v1/inventory/stock/{itemId}/{warehouseId}",
                        "/api/v1/inventory/summary",
                        "/api/v1/inventory/movements",
                        "/api/v1/inventory/adjustments")
                    .uri(destination(environment, "INVENTORY_SERVICE_URL")))
        .build();
  }
}
