package com.sanigest.gateway.infrastructure.routing.company;

import static com.sanigest.gateway.infrastructure.routing.RouteDestination.destination;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class CompanyRoutesConfiguration {
  @Bean
  @Order(2)
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
                        "/api/v1/companies/{companyId}/users/all",
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
}
