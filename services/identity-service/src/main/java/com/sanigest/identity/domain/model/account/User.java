package com.sanigest.identity.domain.model.account;

import com.sanigest.identity.domain.enums.AccountType;
import com.sanigest.identity.domain.enums.UserStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record User(
    UUID id,
    EmailAddress email,
    String passwordHash,
    String name,
    AccountType accountType,
    UserStatus status,
    Instant createdAt,
    Instant updatedAt) {

  public User {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(email, "email must not be null");
    Objects.requireNonNull(passwordHash, "passwordHash must not be null");
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(accountType, "accountType must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    name = name.strip();
    if (name.isEmpty() || name.length() > 160) {
      throw new IllegalArgumentException("name must contain between 1 and 160 characters");
    }
    if (passwordHash.isBlank() || passwordHash.length() > 255) {
      throw new IllegalArgumentException("passwordHash is invalid");
    }
    if (updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("updatedAt must not precede createdAt");
    }
  }

  public static User register(
      UUID id, EmailAddress email, String passwordHash, String name, Instant now) {
    return new User(
        id, email, passwordHash, name, AccountType.REGULAR, UserStatus.ACTIVE, now, now);
  }
}
