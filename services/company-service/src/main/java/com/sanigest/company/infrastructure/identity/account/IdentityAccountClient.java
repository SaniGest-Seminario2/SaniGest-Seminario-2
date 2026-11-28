package com.sanigest.company.infrastructure.identity.account;

import com.sanigest.company.application.account.AccountType;
import com.sanigest.company.application.exception.access.AuthorizationUnavailableException;
import com.sanigest.company.application.exception.access.CompanyAccessDeniedException;
import com.sanigest.company.application.port.out.integration.account.CurrentAccount;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class IdentityAccountClient implements CurrentAccount {
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
  private final Semaphore inFlight = new Semaphore(20);
  private final URI endpoint;

  IdentityAccountClient(@Value("${sanigest.identity-url}") URI origin) {
    if (!("http".equals(origin.getScheme()) || "https".equals(origin.getScheme()))
        || origin.getHost() == null
        || origin.getUserInfo() != null
        || origin.getQuery() != null
        || origin.getFragment() != null
        || !(origin.getPath().isEmpty() || "/".equals(origin.getPath()))) {
      throw new IllegalArgumentException("IDENTITY_SERVICE_URL must be an HTTP(S) origin");
    }
    endpoint = origin.resolve("/internal/v1/account-authorization");
  }

  @Override
  public AccountType resolve(String accessToken) {
    if (!inFlight.tryAcquire()) {
      throw new AuthorizationUnavailableException();
    }
    try {
      var request =
          HttpRequest.newBuilder(endpoint)
              .timeout(Duration.ofSeconds(2))
              .header("Authorization", "Bearer " + accessToken)
              .GET()
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() == 403) {
        throw new CompanyAccessDeniedException();
      }
      if (response.statusCode() != 204) {
        throw new AuthorizationUnavailableException();
      }
      return response
          .headers()
          .firstValue("X-Account-Type")
          .map(AccountType::valueOf)
          .orElseThrow(AuthorizationUnavailableException::new);
    } catch (IOException | IllegalArgumentException failed) {
      throw new AuthorizationUnavailableException();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AuthorizationUnavailableException();
    } finally {
      inFlight.release();
    }
  }
}
