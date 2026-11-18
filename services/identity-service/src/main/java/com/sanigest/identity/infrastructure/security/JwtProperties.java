package com.sanigest.identity.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sanigest.jwt")
record JwtProperties(String issuer, String audience, String secret, Duration accessTokenTtl) {}
