package com.sanigest.finance.infrastructure;

import com.sanigest.security.AccessTokens;
import com.sanigest.security.http.ServletSecurityProblems;
import com.sanigest.web.OpenApiConfiguration;
import jakarta.servlet.DispatcherType;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class FinanceSecurity {
  @Bean
  SecurityFilterChain financeApi(
      HttpSecurity http,
      JsonMapper json,
      @Value("${springdoc.api-docs.enabled:false}") boolean documentationEnabled)
      throws Exception {
    if (documentationEnabled) {
      http.authorizeHttpRequests(requests ->
          requests.requestMatchers(HttpMethod.GET, OpenApiConfiguration.GET_PATHS).permitAll());
    }
    var problems = new ServletSecurityProblems(json, "FORBIDDEN");
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
                    "/actuator/health/readiness",
                    "/actuator/info")
                .permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/finance/audit-log",
                    "/api/v1/cash-sessions/active/summary",
                    "/api/v1/dashboard/projection",
                    "/api/v1/reports/projection")
                .authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/reports/projection/rebuild")
                .authenticated()
                .anyRequest()
                .denyAll())
        .oauth2ResourceServer(resource -> {
          problems.configure(resource);
          resource.jwt(Customizer.withDefaults());
        })
        .securityMatcher("/api/**", "/actuator/**")
        .build();
  }

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${JWT_ISSUER}") String issuer,
      @Value("${JWT_AUDIENCE}") String audience,
      @Value("${JWT_PUBLIC_KEY_LOCATION}") Resource key)
      throws IOException {
    return AccessTokens.servletDecoder(issuer, audience, AccessTokens.verificationKey(key));
  }
}
