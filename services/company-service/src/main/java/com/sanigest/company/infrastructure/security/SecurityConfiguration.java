package com.sanigest.company.infrastructure.security;

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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityConfiguration.JwtProperties.class)
class SecurityConfiguration {
  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, JsonMapper json) throws Exception {
    var problems = new ServletSecurityProblems(json, "FORBIDDEN");
    problems.configure(http);
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a ->
            a.dispatcherTypeMatchers(DispatcherType.ERROR)
                .permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/actuator/health",
                    "/actuator/health/liveness",
                    "/actuator/health/readiness",
                    "/actuator/info")
                .permitAll()
                .anyRequest()
                .authenticated())
        .oauth2ResourceServer(resource -> {
          problems.configure(resource);
          resource.jwt(j -> {});
        })
        .build();
  }

  @Bean
  JwtDecoder jwtDecoder(JwtProperties p) throws IOException {
    return AccessTokens.servletDecoder(
        p.issuer(), p.audience(), AccessTokens.verificationKey(p.publicKey()));
  }

  @ConfigurationProperties("sanigest.jwt")
  record JwtProperties(String issuer, String audience, Resource publicKey) {}
}
