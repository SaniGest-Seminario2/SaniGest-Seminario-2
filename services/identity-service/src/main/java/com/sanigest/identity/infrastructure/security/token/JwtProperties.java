package com.sanigest.identity.infrastructure.security.token;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("sanigest.jwt")
public record JwtProperties(
    String issuer,
    String audience,
    Resource publicKey,
    Resource privateKey,
    Duration accessTokenTtl) {}
