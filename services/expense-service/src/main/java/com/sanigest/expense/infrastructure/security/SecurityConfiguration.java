package com.sanigest.expense.infrastructure.security;

import com.sanigest.security.AccessTokens;
import com.sanigest.security.http.ServletSecurityProblems;
import jakarta.servlet.DispatcherType;
import java.io.IOException;
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
  SecurityFilterChain filterChain(HttpSecurity http, JsonMapper json) throws Exception {
    var problems = new ServletSecurityProblems(json, "EXPENSE_ACCESS_DENIED");
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
                .requestMatchers(HttpMethod.GET, "/actuator/info")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/expenses")
                .authenticated()
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/payables",
                    "/api/v1/payables/all",
                    "/api/v1/payables/supplier-balances")
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
