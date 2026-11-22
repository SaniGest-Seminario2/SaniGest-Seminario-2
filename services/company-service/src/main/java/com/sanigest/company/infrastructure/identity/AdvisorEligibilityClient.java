package com.sanigest.company.infrastructure.identity;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sanigest.company.application.exception.AuthorizationUnavailableException;
import com.sanigest.company.application.port.out.integration.AdvisorEligibility;
import com.sanigest.security.AccessTokens;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

@Component
@EnableConfigurationProperties(AdvisorEligibilityClient.Credentials.class)
final class AdvisorEligibilityClient implements AdvisorEligibility {
  private static final String AUDIENCE = "sanigest-identity-advisor-eligibility";
  private static final Duration TIMEOUT = Duration.ofSeconds(2);
  private static final int MAX_RESPONSE_BYTES = 1024;

  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
  private final Semaphore inFlight = new Semaphore(20);
  private final URI origin;
  private final String issuer;
  private final RSASSASigner signer;
  private final ObjectReader eligibilityResponse;
  private final Clock clock;

  AdvisorEligibilityClient(
      @Value("${sanigest.identity-url}") URI origin,
      Credentials credentials,
      ResourceLoader resources,
      Environment environment,
      ObjectMapper json,
      Clock clock)
      throws IOException {
    this.origin = origin;
    this.issuer = credentials.issuer();
    this.eligibilityResponse =
        json.reader()
            .with(
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    this.clock = clock;
    if (issuer.isBlank() && credentials.privateKeyLocation().isBlank()) {
      signer = null;
      return;
    }
    validateOrigin(origin, environment);
    signer = loadSigner(credentials, resources, environment);
  }

  private static void validateOrigin(URI origin, Environment environment) {
    boolean localHttp =
        "http".equals(origin.getScheme()) && environment.matchesProfiles("local", "test");
    if ((!localHttp && !"https".equals(origin.getScheme()))
        || origin.getHost() == null
        || origin.getUserInfo() != null
        || origin.getQuery() != null
        || origin.getFragment() != null
        || !(origin.getPath().isEmpty() || "/".equals(origin.getPath()))) {
      throw new IllegalArgumentException("Invalid advisor eligibility service origin");
    }
  }

  private static RSASSASigner loadSigner(
      Credentials credentials, ResourceLoader resources, Environment environment)
      throws IOException {
    String keyLocation = credentials.privateKeyLocation();
    if (credentials.issuer().isBlank()
        || credentials.issuer().equals(environment.getRequiredProperty("sanigest.jwt.issuer"))
        || !keyLocation.startsWith("file:")) {
      throw new IllegalArgumentException("Invalid advisor eligibility service credentials");
    }
    Resource userKey =
        resources.getResource(environment.getRequiredProperty("sanigest.jwt.public-key"));
    try (var input = resources.getResource(keyLocation).getInputStream()) {
      var key = RsaKeyConverters.pkcs8().convert(input);
      if (key == null
          || key.getModulus().bitLength() < 2048
          || key.getModulus().equals(AccessTokens.verificationKey(userKey).getModulus())) {
        throw new IllegalArgumentException(
            "Advisor eligibility requires a dedicated RSA signing key");
      }
      return new RSASSASigner(key);
    }
  }

  @Override
  public boolean isEligible(UUID advisorId) {
    if (signer == null || !inFlight.tryAcquire()) {
      throw new AuthorizationUnavailableException();
    }
    try {
      var request =
          HttpRequest.newBuilder(
                  origin.resolve("/internal/v1/accounts/" + advisorId + "/advisor-eligibility"))
              .timeout(TIMEOUT)
              .header("Authorization", "Bearer " + token())
              .header("Accept", "application/json")
              .GET()
              .build();
      var exchange = client.sendAsync(request, response -> boundedBody());
      try {
        var response = exchange.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (response.statusCode() == 200) {
          var eligible = eligibilityResponse.readTree(response.body()).path("eligible");
          if (eligible.isBoolean()) {
            return eligible.booleanValue();
          }
        }
      } finally {
        exchange.cancel(true);
      }
      throw new AuthorizationUnavailableException();
    } catch (JOSEException | ExecutionException | TimeoutException | JacksonException failure) {
      throw new AuthorizationUnavailableException();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AuthorizationUnavailableException();
    } finally {
      inFlight.release();
    }
  }

  private static HttpResponse.BodySubscriber<byte[]> boundedBody() {
    var buffer = new ByteArrayOutputStream();
    var subscriber =
        HttpResponse.BodySubscribers.ofByteArrayConsumer(chunk ->
            chunk.ifPresent(bytes -> {
              if (bytes.length > MAX_RESPONSE_BYTES - buffer.size()) {
                throw new IllegalStateException(
                    "Advisor eligibility response exceeded its size limit");
              }
              buffer.writeBytes(bytes);
            }));
    return HttpResponse.BodySubscribers.mapping(subscriber, ignored -> buffer.toByteArray());
  }

  @ConfigurationProperties("sanigest.advisor-eligibility")
  record Credentials(String issuer, String privateKeyLocation) {}

  private String token() throws JOSEException {
    var now = clock.instant();
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("sanigest-service+jwt"))
                .build(),
            new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("company-service")
                .audience(AUDIENCE)
                .claim("scope", "identity:advisor-eligibility:read")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60)))
                .build());
    jwt.sign(signer);
    return jwt.serialize();
  }
}
