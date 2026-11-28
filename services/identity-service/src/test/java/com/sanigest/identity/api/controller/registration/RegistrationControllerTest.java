package com.sanigest.identity.api.controller.registration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sanigest.identity.application.dto.registration.RegisterUserCommand;
import com.sanigest.identity.application.dto.registration.RegisteredUser;
import com.sanigest.identity.application.exception.registration.DuplicateEmailException;
import com.sanigest.identity.application.usecase.registration.RegisterUserUseCase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RegisterUserUseCase registerUserService;

  @Test
  void returnsCreatedWithUserIdAndEmailOnSuccess() throws Exception {
    var user = new RegisteredUser(USER_ID, "person@example.com");
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

  @ParameterizedTest
  @ValueSource(strings = {"", "{", "null", "{\"email\":{}}"})
  void rejectsMissingOrUnreadableBodiesBeforeCallingTheUseCase(String body) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.traceId").isNotEmpty())
        .andExpect(jsonPath("$.timestamp").isNotEmpty())
        .andExpect(jsonPath("$.detail").isNotEmpty());
    verifyNoInteractions(registerUserService);
  }

  @Test
  void rejectsIncompatibleMediaTypeBeforeCallingTheUseCase() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register").contentType(MediaType.TEXT_PLAIN).content("not json"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
    verifyNoInteractions(registerUserService);
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
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.traceId").isNotEmpty())
        .andExpect(jsonPath("$.errors.email").exists());
    verifyNoInteractions(registerUserService);
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
