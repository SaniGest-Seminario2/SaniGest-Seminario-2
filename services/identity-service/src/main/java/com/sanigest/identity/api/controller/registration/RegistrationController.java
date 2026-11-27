package com.sanigest.identity.api.controller.registration;

import com.sanigest.identity.api.dto.registration.RegisterUserRequest;
import com.sanigest.identity.api.dto.registration.RegisterUserResponse;
import com.sanigest.identity.application.dto.RegisterUserCommand;
import com.sanigest.identity.application.usecase.registration.RegisterUserUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
  @Operation(
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = RegisterUserRequest.class),
                      examples =
                          @ExampleObject(
                              value =
                                  """
                                            {
                                    "name": "Usuario de prueba",
                                    "email": "usuario@example.test",
                                    "password": "<contraseña de desarrollo>"
                                  }
                                  """))))
  @ApiResponse(
      responseCode = "201",
      description = "Created",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = RegisterUserResponse.class),
              examples =
                  @ExampleObject(
                      value =
                          """
                          {"userId":"12345678-1234-4234-8234-123456789abc","email":"usuario@example.test"}
                          """)))
  ResponseEntity<RegisterUserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
    var user =
        registerUserService.register(
            new RegisterUserCommand(request.email(), request.password(), request.name()));
    RegisterUserResponse response = new RegisterUserResponse(user.id(), user.email());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
