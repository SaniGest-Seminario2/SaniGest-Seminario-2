package com.sanigest.identity.infrastructure.security;

import com.sanigest.identity.domain.port.PasswordHasher;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;

final class SpringPasswordHasher implements PasswordHasher {

  private final PasswordEncoder delegate;

  SpringPasswordHasher(PasswordEncoder delegate) {
    this.delegate = Objects.requireNonNull(delegate);
  }

  @Override
  public String hash(CharSequence rawPassword) {
    return delegate.encode(rawPassword);
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    return delegate.matches(rawPassword, encodedPassword);
  }
}
