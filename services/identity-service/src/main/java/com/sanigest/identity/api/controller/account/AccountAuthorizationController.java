package com.sanigest.identity.api.controller.account;

import com.sanigest.identity.application.exception.AuthenticatedUserNotFoundException;
import com.sanigest.identity.application.usecase.account.GetAuthenticatedUserProfileUseCase;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "test"})
final class AccountAuthorizationController {
  private final GetAuthenticatedUserProfileUseCase profiles;

  AccountAuthorizationController(GetAuthenticatedUserProfileUseCase profiles) {
    this.profiles = profiles;
  }

  @GetMapping("/internal/v1/account-authorization")
  ResponseEntity<Void> currentAccount(@AuthenticationPrincipal Jwt jwt) {
    try {
      var profile = profiles.get(jwt.getSubject());
      return ResponseEntity.noContent()
          .header("X-Account-Type", profile.accountType())
          .header("Cache-Control", "no-store")
          .build();
    } catch (AuthenticatedUserNotFoundException denied) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
  }
}
