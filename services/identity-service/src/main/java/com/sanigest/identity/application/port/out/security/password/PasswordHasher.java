package com.sanigest.identity.application.port.out.security.password;

public interface PasswordHasher {

  String hash(CharSequence rawPassword);

  boolean matches(CharSequence rawPassword, String encodedPassword);
}
