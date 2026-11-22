package com.sanigest.companyaccess;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sanigest.companyaccess.CompanyAccessProtocol.Query;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;

final class ServiceAssertion {
  private final String issuer;
  private final RSASSASigner signer;

  ServiceAssertion(String issuer, RSAPrivateKey key) {
    if (issuer.isBlank() || key.getModulus().bitLength() < 2048) {
      throw new IllegalArgumentException("Invalid Company service credentials");
    }
    this.issuer = issuer;
    this.signer = new RSASSASigner(key);
  }

  String sign(Query query) {
    var now = Instant.now();
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(CompanyAccessProtocol.AUDIENCE)
            .subject(query.userId().toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(60)))
            .claim("module", query.module().name())
            .claim("scope", query.scope().name());
    if (query.target() != null) {
      claims.claim("company_id", query.target().toString());
    }
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType(CompanyAccessProtocol.TOKEN_TYPE))
                .build(),
            claims.build());
    try {
      jwt.sign(signer);
      return jwt.serialize();
    } catch (JOSEException failure) {
      throw new CompanyAccessUnavailable();
    }
  }
}
