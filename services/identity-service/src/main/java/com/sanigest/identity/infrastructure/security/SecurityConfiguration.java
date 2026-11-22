package com.sanigest.identity.infrastructure.security;

import com.sanigest.identity.application.port.out.security.TokenIssuer;
import com.sanigest.security.AccessTokens;
import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(JwtProperties.class)
class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests ->
            requests
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout")
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
        .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()))
        .build();
  }

  @Bean
  RSAPublicKey jwtVerificationKey(JwtProperties properties) throws IOException {
    return AccessTokens.verificationKey(properties.publicKey());
  }

  @Bean
  JwtEncoder jwtEncoder(JwtProperties properties, RSAPublicKey verificationKey) throws IOException {
    try (var input = properties.privateKey().getInputStream()) {
      var privateKey = RsaKeyConverters.pkcs8().convert(input);
      if (privateKey == null || !privateKey.getModulus().equals(verificationKey.getModulus())) {
        throw new IllegalArgumentException("JWT private key must match the public key");
      }
      return NimbusJwtEncoder.withKeyPair(verificationKey, privateKey)
          .algorithm(SignatureAlgorithm.RS256)
          .build();
    }
  }

  @Bean
  JwtDecoder jwtDecoder(JwtProperties properties, RSAPublicKey verificationKey) {
    return AccessTokens.servletDecoder(properties.issuer(), properties.audience(), verificationKey);
  }

  @Bean
  TokenIssuer tokenIssuer(JwtEncoder encoder, JwtProperties properties) {
    return new JwtTokenIssuer(encoder, properties);
  }
}
