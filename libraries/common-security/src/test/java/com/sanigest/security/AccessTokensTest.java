package com.sanigest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jwt.JwtException;

class AccessTokensTest {
  private static final String ISSUER = "sanigest";
  private static final String AUDIENCE = "sanigest-api";
  private static final KeyPair KEYS = keyPair();
  private static final RSAPublicKey PUBLIC_KEY = (RSAPublicKey) KEYS.getPublic();
  private static final RSAPrivateKey PRIVATE_KEY = (RSAPrivateKey) KEYS.getPrivate();
  private static final String SUBJECT = UUID.randomUUID().toString();
  private final Instant now = Instant.now();

  @Test
  void acceptsOnlyTheSignedSubjectAsIdentity() throws Exception {
    String jwt = sign(claims().claim("userId", UUID.randomUUID().toString()).build());
    var servletDecoder = AccessTokens.servletDecoder(ISSUER, AUDIENCE, PUBLIC_KEY);
    var reactiveDecoder = AccessTokens.reactiveDecoder(ISSUER, AUDIENCE, PUBLIC_KEY);
    assertThat(servletDecoder.decode(jwt).getSubject()).isEqualTo(SUBJECT);
    assertThat(reactiveDecoder.decode(jwt).block(Duration.ofSeconds(1)).getSubject())
        .isEqualTo(SUBJECT);
  }

  @Test
  void rejectsInvalidClaimsInBothDecoders() throws Exception {
    var servletDecoder = AccessTokens.servletDecoder(ISSUER, AUDIENCE, PUBLIC_KEY);
    var reactiveDecoder = AccessTokens.reactiveDecoder(ISSUER, AUDIENCE, PUBLIC_KEY);
    for (var invalid :
        List.of(
            claims().issuer("other"),
            claims().audience("other"),
            claims().audience((String) null),
            claims().subject("1-1-1-1-1"),
            claims().subject(null),
            claims().issueTime(null),
            claims().issueTime(Date.from(now.plusSeconds(60))),
            claims().expirationTime(null),
            claims().expirationTime(Date.from(now.minusSeconds(1))),
            claims().notBeforeTime(Date.from(now.plusSeconds(10))))) {
      String jwt = sign(invalid.build());
      assertThatThrownBy(() -> servletDecoder.decode(jwt)).isInstanceOf(JwtException.class);
      assertThatThrownBy(() -> reactiveDecoder.decode(jwt).block(Duration.ofSeconds(1)))
          .isInstanceOf(JwtException.class);
    }
  }

  @Test
  void rejectsOtherAlgorithmsAndSignatures() throws Exception {
    var servletDecoder = AccessTokens.servletDecoder(ISSUER, AUDIENCE, PUBLIC_KEY);
    var reactiveDecoder = AccessTokens.reactiveDecoder(ISSUER, AUDIENCE, PUBLIC_KEY);
    var symmetric = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims().build());
    symmetric.sign(new MACSigner(new byte[32]));
    String jwt = sign(claims().build());
    for (String invalid :
        List.of(
            symmetric.serialize(),
            sign(claims().build(), JWSAlgorithm.RS512, PRIVATE_KEY),
            sign(claims().build(), JWSAlgorithm.RS256, (RSAPrivateKey) keyPair().getPrivate()),
            jwt.substring(0, jwt.lastIndexOf('.') + 1) + symmetric.serialize().split("\\.")[2])) {
      assertThatThrownBy(() -> servletDecoder.decode(invalid)).isInstanceOf(JwtException.class);
      assertThatThrownBy(() -> reactiveDecoder.decode(invalid).block(Duration.ofSeconds(1)))
          .isInstanceOf(JwtException.class);
    }
  }

  @Test
  void loadsAnExternalRsaVerificationKey() throws Exception {
    String encoded =
        Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(PUBLIC_KEY.getEncoded());
    String pem = "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n";
    var resource = new ByteArrayResource(pem.getBytes(StandardCharsets.US_ASCII));
    assertThat(AccessTokens.verificationKey(resource)).isEqualTo(PUBLIC_KEY);
  }

  private JWTClaimsSet.Builder claims() {
    return new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .audience(AUDIENCE)
        .subject(SUBJECT)
        .issueTime(Date.from(now.minusSeconds(10)))
        .expirationTime(Date.from(now.plusSeconds(300)));
  }

  private static String sign(JWTClaimsSet claims) throws JOSEException {
    return sign(claims, JWSAlgorithm.RS256, PRIVATE_KEY);
  }

  private static String sign(JWTClaimsSet claims, JWSAlgorithm algorithm, RSAPrivateKey key)
      throws JOSEException {
    var jwt = new SignedJWT(new JWSHeader(algorithm), claims);
    jwt.sign(new RSASSASigner(key));
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
}
