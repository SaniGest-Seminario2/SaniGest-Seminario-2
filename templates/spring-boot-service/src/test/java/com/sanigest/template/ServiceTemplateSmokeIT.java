package com.sanigest.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@Tag("smoke")
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(ServiceTemplateSmokeIT.SmokeScenarioReporter.class)
class ServiceTemplateSmokeIT {

  @LocalServerPort private int port;

  @Test
  @DisplayName("GET actuator health/info/env -> UP, safe metadata and env unavailable")
  void servesHealthAndSafeBuildInformationOverHttp() throws Exception {
    HttpResponse<String> health = get("/actuator/health");
    HttpResponse<String> info = get("/actuator/info");

    assertThat(health.statusCode()).isEqualTo(200);
    assertThat(health.body()).contains("\"status\":\"UP\"").doesNotContain("components");
    assertThat(info.statusCode()).isEqualTo(200);
    assertThat(info.body())
        .contains("\"artifact\":\"spring-boot-service-template\"")
        .doesNotContain("password", "jdbc:", "token");
    assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
  }

  private HttpResponse<String> get(String path) throws Exception {
    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
  }

  static final class SmokeScenarioReporter implements TestWatcher {

    @Override
    public void testSuccessful(ExtensionContext context) {
      System.out.println("[SMOKE PASSED] " + context.getDisplayName());
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
      System.out.println("[SMOKE FAILED] " + context.getDisplayName() + " | " + cause.getMessage());
    }
  }
}
