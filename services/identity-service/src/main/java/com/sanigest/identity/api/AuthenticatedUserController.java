package com.sanigest.identity.api;

import com.sanigest.identity.application.AuthenticatedUserProfile;
import com.sanigest.identity.application.GetAuthenticatedUserProfileService;
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

  private final GetAuthenticatedUserProfileService profiles;

  AuthenticatedUserController(GetAuthenticatedUserProfileService profiles) {
    this.profiles = profiles;
  }

  @GetMapping("/me")
  AuthenticatedUserProfile me(@AuthenticationPrincipal Jwt jwt) {
    return profiles.get(jwt.getSubject());
  }
}
