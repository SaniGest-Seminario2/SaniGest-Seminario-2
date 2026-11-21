package com.sanigest.gateway;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;

final class GatewayTestTokens {
  private static final String ISSUER = "sanigest-test";
  private static final String AUDIENCE = "sanigest-api-test";
  private static final KeyPair KEYS = keyPair();
  private static final Path PUBLIC_KEY = publicPem();

  private GatewayTestTokens() {}

  static void configure(DynamicPropertyRegistry registry) {
    registry.add("JWT_ISSUER", () -> ISSUER);
    registry.add("JWT_AUDIENCE", () -> AUDIENCE);
    registry.add("JWT_PUBLIC_KEY_LOCATION", () -> PUBLIC_KEY.toUri().toString());
  }

  static String validToken() throws JOSEException {
    Instant now = Instant.now();
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .audience(AUDIENCE)
            .subject(UUID.randomUUID().toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .build();
    var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner((RSAPrivateKey) KEYS.getPrivate()));
    return jwt.serialize();
  }

  private static KeyPair keyPair() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static Path publicPem() {
    try {
      var path = Files.createTempFile("gateway-test-jwt", ".pem");
      path.toFile().deleteOnExit();
      var encoder = Base64.getMimeEncoder(64, new byte[] {'\n'});
      return Files.writeString(
          path,
          "-----BEGIN PUBLIC KEY-----\n"
              + encoder.encodeToString(KEYS.getPublic().getEncoded())
              + "\n-----END PUBLIC KEY-----\n");
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }
}
