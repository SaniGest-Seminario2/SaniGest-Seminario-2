package com.sanigest.inventory.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record Warehouse(
    UUID id,
    UUID companyId,
    String code,
    String name,
    String location,
    Status status,
    Instant createdAt,
    Instant updatedAt,
    long revision) {
  public enum Status {
    ACTIVE,
    INACTIVE
  }

  public Warehouse {
    code = requiredText(code, 32).toUpperCase(Locale.ROOT);
    if (!code.matches("[A-Z0-9][A-Z0-9_-]{0,31}") || status == null) {
      throw InventoryViolation.invalid("INVALID_WAREHOUSE");
    }
    name = requiredText(name, 120);
    location = optionalText(location, 240);
  }

  public static String requiredText(String value, int limit) {
    String result = optionalText(value, limit);
    if (result == null || result.isEmpty()) {
      throw InventoryViolation.invalid("REQUIRED_TEXT");
    }
    return result;
  }

  public static String optionalText(String value, int limit) {
    if (value == null) {
      return null;
    }
    String result = value.strip();
    if (result.length() > limit || result.indexOf(0) >= 0) {
      throw InventoryViolation.invalid("INVALID_TEXT");
    }
    return result;
  }

  public void requireActive() {
    if (status != Status.ACTIVE) {
      throw InventoryViolation.conflict("WAREHOUSE_INACTIVE");
    }
  }
}
