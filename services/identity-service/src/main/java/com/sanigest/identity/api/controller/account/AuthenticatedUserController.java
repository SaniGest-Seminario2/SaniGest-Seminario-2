package com.sanigest.identity.api.controller.account;

import com.sanigest.identity.api.dto.account.AuthenticatedUserResponse;
import com.sanigest.identity.application.usecase.account.GetAuthenticatedUserProfileUseCase;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Profile({"local", "test"})
final class AuthenticatedUserController {

  private final GetAuthenticatedUserProfileUseCase profiles;

  AuthenticatedUserController(GetAuthenticatedUserProfileUseCase profiles) {
    this.profiles = profiles;
  }

  @GetMapping("/me")
  AuthenticatedUserResponse me(@AuthenticationPrincipal Jwt jwt) {
    var profile = profiles.get(jwt.getSubject());
    return new AuthenticatedUserResponse(
        profile.id(), profile.name(), profile.email(), profile.accountType());
  }
}
