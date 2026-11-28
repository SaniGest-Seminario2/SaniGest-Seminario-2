package com.sanigest.company.infrastructure.identity.advisor;

import com.sanigest.company.application.exception.access.AuthorizationUnavailableException;
import com.sanigest.company.application.port.out.integration.advisor.AdvisorEligibility;
import com.sanigest.company.infrastructure.identity.IdentityServiceRequest;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@EnableConfigurationProperties(AdvisorEligibilityClient.Credentials.class)
final class AdvisorEligibilityClient implements AdvisorEligibility {
  private final IdentityServiceRequest request;

  AdvisorEligibilityClient(
      @Value("${sanigest.identity-url}") URI origin,
      Credentials credentials,
      ResourceLoader resources,
      Environment environment,
      ObjectMapper json,
      Clock clock)
      throws IOException {
    request =
        new IdentityServiceRequest(
            origin,
            new IdentityServiceRequest.Credentials(
                credentials.issuer(), credentials.privateKeyLocation()),
            resources,
            environment,
            json,
            clock,
            new IdentityServiceRequest.Contract(
                "sanigest-identity-advisor-eligibility",
                "identity:advisor-eligibility:read",
                1024));
  }

  @Override
  public boolean isEligible(UUID advisorId) {
    var response =
        request.exchange("/internal/v1/accounts/" + advisorId + "/advisor-eligibility", null);
    if (response == null || !response.path("eligible").isBoolean()) {
      throw new AuthorizationUnavailableException();
    }
    return response.path("eligible").booleanValue();
  }

  @ConfigurationProperties("sanigest.advisor-eligibility")
  record Credentials(String issuer, String privateKeyLocation) {}
}
