package com.sanigest.identity.infrastructure.security;

import com.sanigest.identity.domain.port.PasswordHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
class PasswordConfiguration {

  private static final int BCRYPT_STRENGTH = 12;

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
  }

  @Bean
  PasswordHasher passwordHasher(PasswordEncoder encoder) {
    return new SpringPasswordHasher(encoder);
  }
}
