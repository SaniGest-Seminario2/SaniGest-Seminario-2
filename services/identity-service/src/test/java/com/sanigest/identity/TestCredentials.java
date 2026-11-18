package com.sanigest.identity;

import java.util.UUID;

final class TestCredentials {

  private TestCredentials() {}

  static String random() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
