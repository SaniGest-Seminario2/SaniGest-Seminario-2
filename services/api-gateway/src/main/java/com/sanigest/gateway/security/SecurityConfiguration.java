package com.sanigest.gateway.security;

import com.sanigest.security.AccessTokens;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {
  @Bean
  SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http, GlobalCorsProperties cors) {
    var source = new UrlBasedCorsConfigurationSource();
    cors.getCorsConfigurations().forEach(source::registerCorsConfiguration);
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(configuration -> configuration.configurationSource(source))
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
        .authorizeExchange(exchanges ->
            exchanges
                .pathMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout")
                .permitAll()
                .pathMatchers(
                    HttpMethod.GET,
                    "/actuator/health",
                    "/actuator/health/liveness",
                    "/actuator/health/readiness")
                .permitAll()
                .anyExchange()
                .authenticated())
        .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()))
        .build();
  }

  @Bean
  ReactiveJwtDecoder jwtDecoder(
      @Value("${JWT_ISSUER}") String issuer,
      @Value("${JWT_AUDIENCE}") String audience,
      @Value("${JWT_PUBLIC_KEY_LOCATION}") Resource publicKey)
      throws IOException {
    return AccessTokens.reactiveDecoder(issuer, audience, AccessTokens.verificationKey(publicKey));
  }
}
