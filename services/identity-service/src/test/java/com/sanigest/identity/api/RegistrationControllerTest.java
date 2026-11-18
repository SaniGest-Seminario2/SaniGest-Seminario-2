package com.sanigest.identity.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sanigest.identity.application.DuplicateEmailException;
import com.sanigest.identity.application.RegisterUserCommand;
import com.sanigest.identity.application.RegisterUserService;
import com.sanigest.identity.domain.model.EmailAddress;
import com.sanigest.identity.domain.model.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RegistrationController.class)
@ActiveProfiles("test")
class RegistrationControllerTest {

  private static final UUID USER_ID = UUID.fromString("9689d726-54cb-4ed5-ab18-339f5d48e379");
  private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RegisterUserService registerUserService;

  @Test
  void returnsCreatedWithUserIdAndEmailOnSuccess() throws Exception {
    User user =
        User.register(
            USER_ID, EmailAddress.of("person@example.com"), "$2a$12$encoded", "Person", NOW);
    when(registerUserService.register(any(RegisterUserCommand.class))).thenReturn(user);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"person@example.com","password":"a-secure-password","name":"Person"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
        .andExpect(jsonPath("$.email").value("person@example.com"));
  }

  @Test
  void returnsBadRequestWhenEmailIsMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"password":"a-secure-password","name":"Person"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.email").exists());
  }

  @Test
  void returnsBadRequestWhenPasswordIsTooShort() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"person@example.com","password":"short","name":"Person"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.password").exists());
  }

  @Test
  void returnsConflictOnDuplicateEmail() throws Exception {
    when(registerUserService.register(any(RegisterUserCommand.class)))
        .thenThrow(new DuplicateEmailException());

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"person@example.com","password":"a-secure-password","name":"Person"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Email already registered"));
  }
}
