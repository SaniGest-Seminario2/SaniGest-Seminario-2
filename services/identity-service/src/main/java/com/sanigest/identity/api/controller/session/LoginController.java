package com.sanigest.identity.api.controller.session;

import com.sanigest.identity.api.dto.session.LoginRequest;
import com.sanigest.identity.api.dto.session.LoginResponse;
import com.sanigest.identity.application.dto.AccessToken;
import com.sanigest.identity.application.usecase.session.LoginUseCase;
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

  private final LoginUseCase loginService;

  LoginController(LoginUseCase loginService) {
    this.loginService = Objects.requireNonNull(loginService);
  }

  @PostMapping("/login")
  ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    AccessToken token = loginService.login(request.email(), request.password());
    return ResponseEntity.ok(LoginResponse.from(token));
  }
}
