package com.sanigest.catalog.infrastructure.security;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sanigest.catalog.application.ClientRepository;
import com.sanigest.catalog.application.CreateClient;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import({SecurityConfiguration.class, CreateClient.class})
class CatalogWebPolicyTest {
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean ClientRepository clients;

  @Test
  void publishesTheOpenApiContractAsJson() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").value("3.0.3"))
        .andExpect(jsonPath("$.paths['/actuator/health/readiness'].get.responses['503']").exists());
  }

  @Test
  void rejectsAnonymousRequestsOutsideTheExactGetAllowlist() throws Exception {
    mvc.perform(get("/api/v1/clients")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/health/db")).andExpect(status().isUnauthorized());
    mvc.perform(post("/v3/api-docs")).andExpect(status().isUnauthorized());
  }

  @Test
  void doesNotAuthorizeBusinessAccessFromAValidIdentityAndClientCompanyId() throws Exception {
    validToken();

    mvc.perform(
            get("/api/v1/clients")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                .param("companyId", "61fe639e-5460-4ca9-8e79-c3bd7ec6190d"))
        .andExpect(status().isForbidden());
  }

  @Test
  void postRequiresAValidJwtAndFailsClosedWithoutCompanyAuthorization() throws Exception {
    mvc.perform(post("/api/v1/clients")).andExpect(status().isUnauthorized());
    given(decoder.decode("invalid")).willThrow(new BadJwtException("Invalid token"));
    mvc.perform(post("/api/v1/clients").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
        .andExpect(status().isUnauthorized());
    validToken();
    mvc.perform(
            post("/api/v1/clients")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                .header("X-Company-Id", "61fe639e-5460-4ca9-8e79-c3bd7ec6190d")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Client\",\"companyId\":\"other\",\"userId\":\"other\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("AUTHORIZATION_UNAVAILABLE"));
    verifyNoInteractions(clients);
  }

  @Test
  void rejectsInvalidClientPayloads() throws Exception {
    validToken();
    for (String body :
        new String[] {
          "{}",
          "{\"name\":\"  \"}",
          "{\"name\":\"Client\",\"email\":\"invalid\"}",
          "{\"name\":\"Client\",\"phone\":\"invalid\"}",
          "{\"name\":\"" + "a".repeat(161) + "\"}",
          "{"
        }) {
      mvc.perform(
              post("/api/v1/clients")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }
    verifyNoInteractions(clients);
  }

  private void validToken() {
    given(decoder.decode("valid"))
        .willReturn(
            Jwt.withTokenValue("valid")
                .header("alg", "RS256")
                .subject("2526a433-d969-4663-9f3a-18693e0757aa")
                .expiresAt(Instant.now().plusSeconds(120))
                .build());
  }

  @Test
  void rejectsAnInvalidBearerToken() throws Exception {
    given(decoder.decode("invalid")).willThrow(new BadJwtException("Invalid token"));
    mvc.perform(get("/api/v1/clients").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
        .andExpect(status().isUnauthorized());
  }
}
