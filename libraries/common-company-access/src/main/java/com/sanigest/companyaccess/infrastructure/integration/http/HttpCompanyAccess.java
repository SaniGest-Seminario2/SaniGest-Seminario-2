package com.sanigest.companyaccess.infrastructure.integration.http;

import com.sanigest.companyaccess.application.exception.CompanyAccessUnavailable;
import com.sanigest.companyaccess.application.port.out.CompanyAccess;
import com.sanigest.companyaccess.infrastructure.integration.protocol.CompanyAccessProtocol;
import com.sanigest.companyaccess.infrastructure.integration.protocol.CompanyAccessProtocol.AccessMode;
import com.sanigest.companyaccess.infrastructure.integration.protocol.CompanyAccessProtocol.Query;
import com.sanigest.companyaccess.infrastructure.integration.protocol.CompanyAccessProtocol.Response;
import com.sanigest.companyaccess.observability.TraceIds;
import com.sanigest.companyaccess.security.authentication.AuthenticatedActor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

public final class HttpCompanyAccess implements CompanyAccess {
  private static final int MAX_RESPONSE_BYTES = 4096;
  private final HttpClient http;
  private final URI endpoint;
  private final Module module;
  private final ServiceAssertion assertion;
  private final Duration responseTimeout;
  private final Supplier<String> traceId;
  private final Semaphore requests = new Semaphore(20);
  private final AccessResponseReader responses;
  private final ObservationRegistry observations;

  public record Settings(
      URI origin,
      boolean allowHttp,
      Module module,
      Duration connectTimeout,
      Duration responseTimeout) {}

  public HttpCompanyAccess(
      Settings settings,
      String issuer,
      RSAPrivateKey key,
      Supplier<String> traceId,
      ObjectMapper json,
      ObservationRegistry observations) {
    URI origin = settings.origin();
    if ((!"https".equals(origin.getScheme())
            && !(settings.allowHttp() && "http".equals(origin.getScheme())))
        || origin.getHost() == null
        || origin.getUserInfo() != null
        || origin.getQuery() != null
        || origin.getFragment() != null
        || !(origin.getPath().isEmpty() || "/".equals(origin.getPath()))) {
      throw new IllegalArgumentException("Invalid Company service origin");
    }
    if (settings.responseTimeout().toMillis() < 1) {
      throw new IllegalArgumentException("Company response timeout must be positive");
    }
    this.endpoint = origin.resolve(CompanyAccessProtocol.PATH);
    this.module = Objects.requireNonNull(settings.module());
    this.assertion = new ServiceAssertion(issuer, key);
    this.http =
        HttpClient.newBuilder()
            .connectTimeout(settings.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    this.responseTimeout = settings.responseTimeout();
    this.traceId = traceId;
    this.responses = new AccessResponseReader(json);
    this.observations = observations;
  }

  @Override
  public Optional<MembershipContext> membership(UUID companyId, Scope scope) {
    return resolve(companyId, scope, AccessMode.MEMBERSHIP)
        .map(response ->
            new MembershipContext(
                response.companyId(),
                response.role(),
                response.explicitGrant(),
                response.companyName()));
  }

  @Override
  public boolean delegated(UUID companyId, Scope scope) {
    return resolve(companyId, scope, AccessMode.DELEGATED).isPresent();
  }

  private Optional<Response> resolve(UUID companyId, Scope scope, AccessMode mode) {
    return AuthenticatedActor.current()
        .flatMap(actor ->
            observe(
                new Query(
                    UUID.fromString(actor.getSubject()),
                    module,
                    Objects.requireNonNull(scope),
                    companyId,
                    mode),
                mode == AccessMode.MEMBERSHIP ? actor.getTokenValue() : null));
  }

  private Optional<Response> observe(Query query, String userAssertion) {
    var observation =
        Observation.createNotStarted("sanigest.company.access", observations)
            .lowCardinalityKeyValue("module", module.name());
    return observation.observe(() -> {
      try {
        var answer = exchange(query, userAssertion);
        observation.lowCardinalityKeyValue("outcome", answer.isPresent() ? "resolved" : "denied");
        return answer;
      } catch (CompanyAccessUnavailable failure) {
        observation.lowCardinalityKeyValue("outcome", "unavailable");
        throw failure;
      }
    });
  }

  private Optional<Response> exchange(Query query, String userAssertion) {
    String trace = TraceIds.normalize(traceId.get());
    if (!requests.tryAcquire()) {
      throw unavailable(trace, "concurrency_limit");
    }
    try {
      var request =
          HttpRequest.newBuilder(endpoint)
              .timeout(responseTimeout)
              .header("Authorization", "Bearer " + assertion.sign(query))
              .header("Accept", "application/json")
              .header("X-Trace-Id", trace)
              .header(
                  "traceparent",
                  "00-"
                      + trace
                      + "-"
                      + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                      + "-00")
              .GET();
      if (userAssertion != null) {
        request.header(CompanyAccessProtocol.USER_ASSERTION, userAssertion);
      }
      var call = http.sendAsync(request.build(), response -> boundedBody());
      try {
        var response = call.get(responseTimeout.toMillis(), TimeUnit.MILLISECONDS);
        return switch (response.statusCode()) {
          case 200 -> {
            String mediaType =
                response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].strip();
            if (!"application/json".equalsIgnoreCase(mediaType)) {
              throw new CompanyAccessUnavailable();
            }
            yield Optional.of(responses.read(response.body(), query));
          }
          case 403 -> Optional.empty();
          default -> throw new CompanyAccessUnavailable();
        };
      } finally {
        call.cancel(true);
      }
    } catch (CompanyAccessUnavailable | ExecutionException failure) {
      throw unavailable(trace, "remote_failure");
    } catch (TimeoutException timeout) {
      throw unavailable(trace, "timeout");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw unavailable(trace, "interrupted");
    } finally {
      requests.release();
    }
  }

  private static CompanyAccessUnavailable unavailable(String trace, String reason) {
    LoggerFactory.getLogger(HttpCompanyAccess.class)
        .atWarn()
        .addKeyValue("traceId", trace)
        .addKeyValue("reason", reason)
        .log("Company authorization query failed");
    return new CompanyAccessUnavailable();
  }

  private static HttpResponse.BodySubscriber<byte[]> boundedBody() {
    var buffer = new ByteArrayOutputStream();
    var subscriber =
        HttpResponse.BodySubscribers.ofByteArrayConsumer(chunk ->
            chunk.ifPresent(bytes -> {
              if (bytes.length > MAX_RESPONSE_BYTES - buffer.size()) {
                throw new CompanyAccessUnavailable();
              }
              buffer.writeBytes(bytes);
            }));
    return HttpResponse.BodySubscribers.mapping(subscriber, ignored -> buffer.toByteArray());
  }
}
