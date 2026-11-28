package com.sanigest.inventory.infrastructure.security;

import com.sanigest.security.api.error.ServletSecurityProblems;
import com.sanigest.security.token.AccessTokens;
import com.sanigest.web.api.documentation.OpenApiConfiguration;
import jakarta.servlet.DispatcherType;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityConfiguration.JwtProperties.class)
class SecurityConfiguration {
  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http,
      JsonMapper json,
      @Value("${springdoc.api-docs.enabled:false}") boolean documentationEnabled)
      throws Exception {
    if (documentationEnabled) {
      http.authorizeHttpRequests(requests ->
          requests.requestMatchers(HttpMethod.GET, OpenApiConfiguration.GET_PATHS).permitAll());
    }
    var problems = new ServletSecurityProblems(json, "INVENTORY_ACCESS_DENIED");
    problems.configure(http);
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests ->
            requests
                .dispatcherTypeMatchers(DispatcherType.ERROR)
                .permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/actuator/health",
                    "/actuator/health/liveness",
                    "/actuator/health/readiness")
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/info", "/inventory-openapi.json")
                .permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/warehouses",
                    "/api/v1/warehouses/{id}",
                    "/api/v1/inventory/audit-log",
                    "/api/v1/inventory/options",
                    "/api/v1/inventory/stock",
                    "/api/v1/inventory/stock/{itemId}/{warehouseId}",
                    "/api/v1/inventory/movements",
                    "/api/v1/inventory/summary")
                .authenticated()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/warehouses",
                    "/api/v1/inventory/adjustments",
                    "/internal/v1/inventory/purchase-receipts")
                .authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/warehouses/{id}")
                .authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/v1/inventory/stock/{itemId}/{warehouseId}")
                .authenticated()
                .anyRequest()
                .denyAll())
        .oauth2ResourceServer(resource -> {
          problems.configure(resource);
          resource.jwt(Customizer.withDefaults());
        })
        .build();
  }

  @Bean
  JwtDecoder jwtDecoder(JwtProperties properties) throws IOException {
    return AccessTokens.servletDecoder(
        properties.issuer(),
        properties.audience(),
        AccessTokens.verificationKey(properties.publicKey()));
  }

  @ConfigurationProperties("sanigest.jwt")
  record JwtProperties(String issuer, String audience, Resource publicKey) {}
}
