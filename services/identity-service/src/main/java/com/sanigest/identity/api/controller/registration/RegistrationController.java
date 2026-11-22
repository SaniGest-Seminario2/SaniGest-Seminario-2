package com.sanigest.identity.api.controller.registration;

import com.sanigest.identity.api.dto.registration.RegisterUserRequest;
import com.sanigest.identity.api.dto.registration.RegisterUserResponse;
import com.sanigest.identity.application.dto.RegisterUserCommand;
import com.sanigest.identity.application.usecase.registration.RegisterUserUseCase;
import com.sanigest.identity.domain.model.User;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Profile({"local", "test"})
public final class RegistrationController {

  private final RegisterUserUseCase registerUserService;

  RegistrationController(RegisterUserUseCase registerUserService) {
    this.registerUserService = Objects.requireNonNull(registerUserService);
  }

  @PostMapping("/register")
  ResponseEntity<RegisterUserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
    User user =
        registerUserService.register(
            new RegisterUserCommand(request.email(), request.password(), request.name()));
    RegisterUserResponse response = new RegisterUserResponse(user.id(), user.email().value());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
