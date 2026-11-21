package com.sanigest.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sanigest.identity.application.AuthenticatedUserNotFoundException;
import com.sanigest.identity.application.AuthenticatedUserProfile;
import com.sanigest.identity.application.GetAuthenticatedUserProfileService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class AccountAuthorizationControllerTest {
  private final GetAuthenticatedUserProfileService profiles =
      mock(GetAuthenticatedUserProfileService.class);
  private final AccountAuthorizationController controller =
      new AccountAuthorizationController(profiles);
  private final UUID userId = UUID.randomUUID();
  private final Jwt jwt =
      Jwt.withTokenValue("validated")
          .header("alg", "RS256")
          .subject(userId.toString())
          .claim("userId", UUID.randomUUID().toString())
          .claim("accountType", "REGULAR")
          .build();

  @Test
  void returnsTheCurrentAccountTypeForTheAuthenticatedSubject() {
    when(profiles.get(userId.toString()))
        .thenReturn(new AuthenticatedUserProfile(userId, "Advisor", "test@example.com", "ADVISOR"));
    var response = controller.currentAccount(jwt);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getHeaders().getFirst("X-Account-Type")).isEqualTo("ADVISOR");
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
  }

  @Test
  void deniesMissingOrDisabledAccounts() {
    when(profiles.get(userId.toString())).thenThrow(new AuthenticatedUserNotFoundException());
    var response = controller.currentAccount(jwt);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getHeaders().getFirst("X-Account-Type")).isNull();
  }
}
