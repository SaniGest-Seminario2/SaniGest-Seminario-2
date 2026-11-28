package com.sanigest.identity.integration;

import static com.sanigest.identity.integration.CompanyServiceTestTokens.SCOPE;
import static com.sanigest.identity.integration.CompanyServiceTestTokens.TYPE;
import static com.sanigest.identity.integration.CompanyServiceTestTokens.claims;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sanigest.identity.application.port.out.persistence.account.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class AdvisorEligibilitySecurityTest {
  private static final String PATH = "/internal/v1/accounts/{id}/advisor-eligibility";
  private static final UUID TARGET = UUID.fromString("a1e6b198-89bd-420c-84dd-8694b6354469");
  private static final CompanyServiceTestTokens SERVICE = new CompanyServiceTestTokens();

  @Autowired private MockMvc mvc;
  @Autowired private JwtEncoder userEncoder;
  @MockitoBean private UserRepository users;

  @DynamicPropertySource
  static void serviceCredentials(DynamicPropertyRegistry registry) {
    SERVICE.configure(registry);
  }

  @Test
  void rejectsAnUnauthenticatedCallerWithSpoofedIdentityHeaders() throws Exception {
    mvc.perform(
            get(PATH, TARGET)
                .header("X-Service-Name", "company-service")
                .header("X-User-Id", TARGET))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("Cache-Control", "no-store"));
    verifyNoInteractions(users);
  }

  @Test
  void rejectsAUserJwtEvenWithSpoofedScopeRoleAndTarget() throws Exception {
    String token =
        CompanyServiceTestTokens.encode(
            userEncoder,
            claims()
                .issuer("sanigest-test")
                .audience(List.of("sanigest-api-test"))
                .subject(UUID.randomUUID().toString())
                .claim("scope", SCOPE)
                .claim("role", "ADVISOR"),
            "JWT");
    mvc.perform(
            get(PATH, TARGET)
                .header("Authorization", "Bearer " + token)
                .header("X-Service-Name", "company-service")
                .param("userId", "other"))
        .andExpect(status().isForbidden())
        .andExpect(header().string("Cache-Control", "no-store"));
    verifyNoInteractions(users);
  }

  @Test
  void acceptsOnlyTheDedicatedScopeAndCompanySubject() throws Exception {
    String otherUserId = "36ea33a7-dcee-4650-a70a-3af144501d29";
    mvc.perform(
            get(PATH, TARGET)
                .header("Authorization", "Bearer " + SERVICE.validToken())
                .header("X-User-Id", otherUserId)
                .param("userId", otherUserId))
        .andExpect(status().isOk())
        .andExpect(content().string("{\"eligible\":false}"))
        .andExpect(header().string("Cache-Control", "no-store"));

    String[] forbidden = {
      SERVICE.issue(claims(), TYPE),
      SERVICE.issue(claims().claim("scope", "other " + SCOPE + "-extra"), TYPE),
      SERVICE.issue(claims().claim("scope", List.of(SCOPE)), TYPE),
      SERVICE.issue(claims().subject("catalog-service").claim("scope", SCOPE), TYPE)
    };
    for (String token : forbidden) {
      mvc.perform(get(PATH, TARGET).header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
    }
    verify(users).findById(TARGET);
    verifyNoMoreInteractions(users);
  }

  @ParameterizedTest
  @MethodSource("invalidTokens")
  void rejectsInvalidServiceClaims(String token) throws Exception {
    mvc.perform(get(PATH, TARGET).header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(users);
  }

  static Stream<String> invalidTokens() {
    return Stream.of(
        SERVICE.issue(claims().claim("scope", SCOPE).issuer("wrong-issuer"), TYPE),
        SERVICE.issue(claims().claim("scope", SCOPE).audience(List.of("wrong-audience")), TYPE),
        SERVICE.issue(
            claims().claim("scope", SCOPE).expiresAt(Instant.now().minusSeconds(1)), TYPE),
        SERVICE.issue(
            claims().claim("scope", SCOPE).expiresAt(Instant.now().plusSeconds(600)), TYPE),
        SERVICE.issue(claims().claim("scope", SCOPE), "JWT"));
  }

  @Test
  void rejectsServiceClaimsSignedWithTheSessionKey() throws Exception {
    String token =
        CompanyServiceTestTokens.encode(userEncoder, claims().claim("scope", SCOPE), TYPE);
    mvc.perform(get(PATH, TARGET).header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(users);
  }

  @Test
  void rejectsServiceCredentialsOnUserEndpoints() throws Exception {
    String authorization = "Bearer " + SERVICE.validToken();
    mvc.perform(
            get("/internal/v1/account-authorization")
                .param("userId", TARGET.toString())
                .header("Authorization", authorization))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", authorization))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(users);
  }

  @Test
  void rejectsAnInvalidUuidWithASafeError() throws Exception {
    mvc.perform(get(PATH, "1-2-3-4-5").header("Authorization", "Bearer " + SERVICE.validToken()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("userId must be a UUID"))
        .andExpect(header().string("Cache-Control", "no-store"));
    verifyNoInteractions(users);
  }

  @Test
  void failsClosedIfTheRepositoryIsUnavailable() throws Exception {
    when(users.findById(TARGET))
        .thenThrow(new DataAccessResourceFailureException("private database details"));
    mvc.perform(get(PATH, TARGET).header("Authorization", "Bearer " + SERVICE.validToken()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.detail").value("Advisor eligibility is temporarily unavailable"))
        .andExpect(jsonPath("$.eligible").doesNotExist())
        .andExpect(header().string("Cache-Control", "no-store"));
  }
}
