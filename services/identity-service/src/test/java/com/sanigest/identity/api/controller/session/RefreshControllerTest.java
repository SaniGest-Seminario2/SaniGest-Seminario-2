package com.sanigest.identity.api.controller.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sanigest.identity.api.dto.session.LoginResponse;
import com.sanigest.identity.api.dto.session.RefreshRequest;
import com.sanigest.identity.application.dto.AccessToken;
import com.sanigest.identity.application.port.in.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RefreshControllerTest {

  private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
  private final RefreshController controller = new RefreshController(refreshTokens);

  @Test
  void refreshReturnsRotatedAccessAndRefreshTokens() {
    when(refreshTokens.refresh("refresh-token"))
        .thenReturn(new AccessToken("access-token", "Bearer", 900, "rotated-token", 604800));

    var response = controller.refresh(new RefreshRequest("refresh-token"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .isEqualTo(new LoginResponse("access-token", "Bearer", 900, "rotated-token", 604800));
  }

  @Test
  void logoutRevokesRefreshTokenAndReturnsNoContent() {
    var response = controller.logout(new RefreshRequest("refresh-token"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(refreshTokens).logout("refresh-token");
  }
}
