package com.sanigest.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sanigest.identity.domain.model.EmailAddress;
import com.sanigest.identity.domain.model.User;
import com.sanigest.identity.domain.model.UserStatus;
import com.sanigest.identity.domain.port.PasswordHasher;
import com.sanigest.identity.domain.port.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterUserServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");
  private static final UUID USER_ID = UUID.fromString("9689d726-54cb-4ed5-ab18-339f5d48e379");

  @Mock private UserRepository repository;
  @Mock private PasswordHasher hasher;

  private RegisterUserService service;

  @BeforeEach
  void setUp() {
    service =
        new RegisterUserService(
            repository, hasher, Clock.fixed(NOW, ZoneOffset.UTC), () -> USER_ID);
  }

  @Test
  void registersAnActiveUserWithNormalizedEmailAndEncodedPassword() {
    when(hasher.hash("a-secure-password")).thenReturn("$2a$12$encoded");
    when(repository.save(org.mockito.ArgumentMatchers.any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    User user =
        service.register(
            new RegisterUserCommand(" Person@Example.COM ", "a-secure-password", " Person "));

    assertThat(user.id()).isEqualTo(USER_ID);
    assertThat(user.email()).isEqualTo(EmailAddress.of("person@example.com"));
    assertThat(user.passwordHash()).isEqualTo("$2a$12$encoded");
    assertThat(user.passwordHash()).doesNotContain("a-secure-password");
    assertThat(user.name()).isEqualTo("Person");
    assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.createdAt()).isEqualTo(NOW);
  }

  @Test
  void rejectsDuplicateEmailBeforeHashing() {
    EmailAddress email = EmailAddress.of("person@example.com");
    when(repository.existsByEmail(email)).thenReturn(true);

    assertThatThrownBy(() ->
            service.register(
                new RegisterUserCommand("PERSON@example.com", "a-secure-password", "Person")))
        .isInstanceOf(DuplicateEmailException.class);

    verify(hasher, never()).hash(org.mockito.ArgumentMatchers.any());
    verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void rejectsWeakPassword() {
    assertThatThrownBy(() ->
            service.register(new RegisterUserCommand("person@example.com", "short", "Person")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("12");
  }
}
