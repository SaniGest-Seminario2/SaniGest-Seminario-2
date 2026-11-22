package com.sanigest.company.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** End-to-end smoke for disposable deployments with both services running. */
@Tag("smoke")
@EnabledIfSystemProperty(named = "smoke.identity.url", matches = ".+")
class CompanyProfileSmokeIT {
  private static final String COMPANIES = "/api/v1/companies";
  private static final String ME = COMPANIES + "/me";
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  void authenticatesAndPersistsCompanyProfileChanges() throws Exception {
    String identityUrl = System.getProperty("smoke.identity.url");
    String companyUrl = System.getProperty("smoke.company.url");
    assertThat(companyUrl).as("smoke.company.url").isNotBlank();

    String runId = UUID.randomUUID().toString();
    String email = "company-smoke-" + runId + "@example.com";
    var credentials =
        json.createObjectNode().put("email", email).put("password", UUID.randomUUID().toString());
    send(
        identityUrl,
        "/api/v1/auth/register",
        "POST",
        201,
        credentials.deepCopy().put("name", "Company smoke"),
        null,
        null);
    var login = send(identityUrl, "/api/v1/auth/login", "POST", 200, credentials, null, null);
    String token = login.path("accessToken").stringValue();
    assertThat(token).isNotBlank();

    var profile =
        json.createObjectNode()
            .put("companyName", "Company Smoke SRL")
            .put("businessName", "Company Smoke")
            .put("location", "Santo Domingo");
    String phone = "8095555555";
    var created =
        send(
            companyUrl,
            COMPANIES,
            "POST",
            201,
            profile.deepCopy().put("email", email).put("phone", phone),
            token,
            runId);
    String companyId = created.path("companyId").stringValue();
    assertThat(companyId).isNotBlank();
    profile
        .put("companyId", companyId)
        .put("currency", "DOP")
        .put("status", "ACTIVE")
        .putNull("rnc");
    assertThat(send(companyUrl, ME, "GET", 200, null, token, null)).isEqualTo(profile);

    var changes =
        json.createObjectNode()
            .put("businessName", "Company Smoke Updated")
            .put("location", "Santiago");
    profile.setAll(changes);
    var updated = send(companyUrl, ME, "PATCH", 200, changes, token, null);
    String updatedAt = updated.path("updatedAt").stringValue();
    assertThat(updatedAt).isNotBlank();
    Instant.parse(updatedAt);
    assertThat(updated)
        .isEqualTo(
            profile.deepCopy().put("email", email).put("phone", phone).put("updatedAt", updatedAt));
    assertThat(send(companyUrl, ME, "GET", 200, null, token, null)).isEqualTo(profile);
    send(companyUrl, ME, "GET", 401, null, null, null);
    String outsiderEmail = "outsider-" + runId + "@example.com";
    var outsider = credentials.deepCopy().put("email", outsiderEmail);
    send(
        identityUrl,
        "/api/v1/auth/register",
        "POST",
        201,
        outsider.deepCopy().put("name", "Outside company"),
        null,
        null);
    String outsiderToken =
        send(identityUrl, "/api/v1/auth/login", "POST", 200, outsider, null, null)
            .path("accessToken")
            .stringValue();
    send(companyUrl, ME + "?companyId=" + companyId, "PATCH", 404, changes, outsiderToken, null);
  }

  private JsonNode send(
      String baseUrl,
      String path,
      String method,
      int status,
      JsonNode payload,
      String token,
      String key)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create(baseUrl.replaceFirst("/+$", "") + path))
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(10));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    var body = HttpRequest.BodyPublishers.noBody();
    if (payload != null) {
      request.header("Content-Type", "application/json");
      body = HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload));
    }
    var response =
        http.send(request.method(method, body).build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as("%s %s", method, path).isEqualTo(status);
    return response.body().isBlank() ? null : json.readTree(response.body());
  }
}
