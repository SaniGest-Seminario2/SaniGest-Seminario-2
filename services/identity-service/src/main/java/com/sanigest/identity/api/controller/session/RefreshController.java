package com.sanigest.identity.api.controller.session;

import com.sanigest.identity.api.dto.session.LoginResponse;
import com.sanigest.identity.api.dto.session.RefreshRequest;
import com.sanigest.identity.application.port.in.session.RefreshTokenService;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "test"})
@RequestMapping("/api/v1/auth")
final class RefreshController {
  private final RefreshTokenService refreshTokens;

  RefreshController(RefreshTokenService refreshTokens) {
    this.refreshTokens = Objects.requireNonNull(refreshTokens);
  }

  @PostMapping("/refresh")
  ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    return ResponseEntity.ok(LoginResponse.from(refreshTokens.refresh(request.refreshToken())));
  }

  @PostMapping("/logout")
  ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
    refreshTokens.logout(request.refreshToken());
    return ResponseEntity.noContent().build();
  }
}
