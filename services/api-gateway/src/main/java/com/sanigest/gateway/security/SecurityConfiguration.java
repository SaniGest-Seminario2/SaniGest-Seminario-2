package com.sanigest.gateway.security;

import com.sanigest.gateway.error.GatewayProblems;
import com.sanigest.gateway.routing.ServiceRoutes;
import com.sanigest.security.AccessTokens;
import com.sanigest.web.HttpProblems;
import com.sanigest.web.OpenApiConfiguration;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.access.server.BearerTokenServerAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.server.BearerTokenServerAuthenticationEntryPoint;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {
  @Bean
  SecurityWebFilterChain securityFilterChain(
      ServerHttpSecurity http,
      GlobalCorsProperties cors,
      GatewayProblems problems,
      @Value("${springdoc.api-docs.enabled:false}") boolean documentationEnabled) {
    var bearer = new BearerTokenServerAuthenticationEntryPoint();
    ServerAuthenticationEntryPoint entry =
        (exchange, failure) ->
            bearer.commence(withProblemOnCompletion(exchange, problems, null), failure);
    var bearerDenied = new BearerTokenServerAccessDeniedHandler();
    ServerAccessDeniedHandler denied =
        (exchange, failure) ->
            bearerDenied.handle(withProblemOnCompletion(exchange, problems, "FORBIDDEN"), failure);
    var source = new UrlBasedCorsConfigurationSource();
    cors.getCorsConfigurations().forEach(source::registerCorsConfiguration);
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(configuration -> configuration.configurationSource(source))
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
        .authorizeExchange(exchanges -> {
          if (documentationEnabled) {
            exchanges.pathMatchers(HttpMethod.GET, OpenApiConfiguration.GET_PATHS).permitAll();
            for (String module : ServiceRoutes.DOCUMENTED_MODULES) {
              exchanges.pathMatchers(HttpMethod.GET, "/v3/api-docs/" + module).permitAll();
            }
          }
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
              .authenticated();
        })
        .exceptionHandling(
            errors -> errors.authenticationEntryPoint(entry).accessDeniedHandler(denied))
        .oauth2ResourceServer(resource ->
            resource
                .authenticationEntryPoint(entry)
                .accessDeniedHandler(denied)
                .jwt(Customizer.withDefaults()))
        .build();
  }

  private static ServerWebExchange withProblemOnCompletion(
      ServerWebExchange exchange, GatewayProblems problems, String code) {
    return exchange
        .mutate()
        .response(
            new ServerHttpResponseDecorator(exchange.getResponse()) {
              @Override
              public Mono<Void> setComplete() {
                var headers = getHeaders();
                String challenge = headers.getFirst(HttpHeaders.WWW_AUTHENTICATE);
                if (challenge != null && challenge.contains(", error_description=")) {
                  headers.set(
                      HttpHeaders.WWW_AUTHENTICATE,
                      challenge.substring(0, challenge.indexOf(", error_description=")));
                }
                var status = getStatusCode();
                return problems.write(
                    exchange, status, code == null ? HttpProblems.code(status) : code);
              }
            })
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
