package com.sanigest.identity.api;

import com.sanigest.identity.application.AccessToken;
import com.sanigest.identity.application.LoginService;
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
public final class LoginController {

  private final LoginService loginService;

  LoginController(LoginService loginService) {
    this.loginService = Objects.requireNonNull(loginService);
  }

  @PostMapping("/login")
  ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    AccessToken token = loginService.login(request.email(), request.password());
    return ResponseEntity.ok(new LoginResponse(token.value(), token.type(), token.expiresIn()));
  }
}
