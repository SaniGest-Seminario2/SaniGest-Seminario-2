package com.sanigest.catalog.domain.model;

import com.sanigest.catalog.domain.exception.InvalidClientPatch;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record ClientPatch(Map<Field, String> values) {
  public enum Field {
    NAME("name"),
    TAX_ID("taxId"),
    EMAIL("email"),
    PHONE("phone"),
    ADDRESS("address");

    private final String property;

    Field(String property) {
      this.property = property;
    }

    public String property() {
      return property;
    }
  }

  public ClientPatch {
    if (values.isEmpty()) {
      throw new InvalidClientPatch();
    }
    values = Collections.unmodifiableMap(new EnumMap<>(values));
  }

  ClientDetails applyTo(ClientDetails current) {
    var name = values.getOrDefault(Field.NAME, current.name());
    if (name == null || name.isBlank()) {
      throw new InvalidClientPatch();
    }
    return new ClientDetails(
        name,
        values.getOrDefault(Field.TAX_ID, current.taxId()),
        values.getOrDefault(Field.EMAIL, current.email()),
        values.getOrDefault(Field.PHONE, current.phone()),
        values.getOrDefault(Field.ADDRESS, current.address()));
  }
}
