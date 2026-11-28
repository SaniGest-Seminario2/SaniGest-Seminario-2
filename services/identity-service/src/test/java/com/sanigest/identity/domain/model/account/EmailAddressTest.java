package com.sanigest.identity.domain.model.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailAddressTest {

  @Test
  void normalizesCaseAndSurroundingWhitespace() {
    assertThat(EmailAddress.of("  User.Name@Example.COM ").value())
        .isEqualTo("user.name@example.com");
  }

  @Test
  void rejectsMalformedAddresses() {
    assertThatThrownBy(() -> EmailAddress.of("not-an-email"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
