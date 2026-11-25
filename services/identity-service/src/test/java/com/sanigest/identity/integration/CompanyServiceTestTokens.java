package com.sanigest.identity.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;

final class CompanyServiceTestTokens {
  static final String ISSUER = "sanigest-company-test";
  static final String AUDIENCE = "sanigest-identity-advisor-eligibility";
  static final String SUBJECT = "company-service";
  static final String SCOPE = "identity:advisor-eligibility:read";
  static final String TYPE = "sanigest-service+jwt";

  private final JwtEncoder encoder;
  private final Path publicKeyFile;

  CompanyServiceTestTokens() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      var keyPair = generator.generateKeyPair();
      encoder =
          NimbusJwtEncoder.withKeyPair(
                  (RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate())
              .build();
      publicKeyFile = Files.createTempFile("company-public-key-", ".pem");
      publicKeyFile.toFile().deleteOnExit();
      Files.writeString(
          publicKeyFile,
          "-----BEGIN PUBLIC KEY-----\n"
              + Base64.getMimeEncoder().encodeToString(keyPair.getPublic().getEncoded())
              + "\n-----END PUBLIC KEY-----\n");
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot create temporary test credentials", exception);
    }
  }

  void configure(DynamicPropertyRegistry registry) {
    configure(registry, "sanigest.advisor-eligibility", ISSUER);
  }

  void configure(DynamicPropertyRegistry registry, String property, String issuer) {
    registry.add(property + ".issuer", () -> issuer);
    registry.add(property + ".public-key-location", () -> publicKeyFile.toUri());
  }

  String validToken() {
    return issue(claims().claim("scope", SCOPE), TYPE);
  }

  String issue(JwtClaimsSet.Builder claims, String type) {
    return encode(encoder, claims, type);
  }

  static JwtClaimsSet.Builder claims() {
    Instant now = Instant.now();
    return JwtClaimsSet.builder()
        .issuer(ISSUER)
        .audience(List.of(AUDIENCE))
        .subject(SUBJECT)
        .issuedAt(now.minusSeconds(5))
        .expiresAt(now.plusSeconds(60));
  }

  static String encode(JwtEncoder encoder, JwtClaimsSet.Builder claims, String type) {
    var header = JwsHeader.with(SignatureAlgorithm.RS256).type(type).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
  }
}
