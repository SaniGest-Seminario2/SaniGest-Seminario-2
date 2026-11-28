package com.sanigest.company.infrastructure.identity.team;

import com.sanigest.company.application.exception.access.AuthorizationUnavailableException;
import com.sanigest.company.application.port.out.integration.team.MemberIdentityDirectory;
import com.sanigest.company.domain.model.team.Invitation;
import com.sanigest.company.infrastructure.identity.IdentityServiceRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
final class IdentityMemberDirectoryClient implements MemberIdentityDirectory {
  private final IdentityServiceRequest request;
  private final ObjectMapper json;

  IdentityMemberDirectoryClient(
      @Value("${sanigest.identity-url}") URI origin,
      @Value("${sanigest.member-directory.issuer:}") String issuer,
      @Value("${sanigest.member-directory.private-key-location:}") String keyLocation,
      ResourceLoader resources,
      Environment environment,
      ObjectMapper json,
      Clock clock)
      throws IOException {
    this.json = json;
    request =
        new IdentityServiceRequest(
            origin,
            new IdentityServiceRequest.Credentials(issuer, keyLocation),
            resources,
            environment,
            json,
            clock,
            new IdentityServiceRequest.Contract(
                "sanigest-identity-member-directory", "identity:member-directory:read", 512_000));
  }

  @Override
  public Map<UUID, Identity> lookup(List<UUID> userIds) {
    if (userIds.isEmpty() || userIds.size() > MAX_BATCH) {
      throw new IllegalArgumentException("Invalid member directory batch size");
    }
    var body =
        HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(Map.of("userIds", userIds)));
    var response = request.exchange("/internal/v1/member-directory", body);
    Set<UUID> requested = Set.copyOf(userIds);
    if (response == null || !response.isArray() || response.size() > requested.size()) {
      throw new AuthorizationUnavailableException();
    }
    Map<UUID, Identity> result = new HashMap<>();
    try {
      for (var member : response) {
        var id = responseUserId(member.path("userId"));
        if (!requested.contains(id) || result.putIfAbsent(id, responseIdentity(member)) != null) {
          throw new AuthorizationUnavailableException();
        }
      }
    } catch (IllegalArgumentException invalidResponse) {
      throw new AuthorizationUnavailableException();
    }
    return Map.copyOf(result);
  }

  private static UUID responseUserId(JsonNode value) {
    if (!value.isString()) {
      throw new AuthorizationUnavailableException();
    }
    var id = UUID.fromString(value.asString());
    if (!id.toString().equalsIgnoreCase(value.asString())) {
      throw new AuthorizationUnavailableException();
    }
    return id;
  }

  private static Identity responseIdentity(JsonNode member) {
    var name = member.path("name");
    var email = member.path("email");
    var verified = member.path("emailVerified");
    if (!name.isString()
        || name.asString().isBlank()
        || name.asString().length() > 160
        || !email.isString()
        || !verified.isBoolean()) {
      throw new AuthorizationUnavailableException();
    }
    return new Identity(
        name.asString(), Invitation.normalizeEmail(email.asString()), verified.booleanValue());
  }
}
