package com.sanigest.identity.application.port.out.security;

public interface PasswordHasher {

  String hash(CharSequence rawPassword);

  boolean matches(CharSequence rawPassword, String encodedPassword);
}
