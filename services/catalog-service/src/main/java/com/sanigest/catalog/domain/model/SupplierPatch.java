package com.sanigest.catalog.domain.model;

import com.sanigest.catalog.domain.exception.InvalidSupplierPatch;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record SupplierPatch(Map<Field, String> values) {
  public enum Field {
    NAME("name"),
    TAX_ID("taxId"),
    CONTACT("contact"),
    PHONE("phone"),
    EMAIL("email");

    private final String property;

    Field(String property) {
      this.property = property;
    }

    public String property() {
      return property;
    }
  }

  public SupplierPatch {
    if (values.isEmpty()) {
      throw new InvalidSupplierPatch();
    }
    values = Collections.unmodifiableMap(new EnumMap<>(values));
  }

  SupplierDetails applyTo(SupplierDetails current) {
    return new SupplierDetails(
        values.getOrDefault(Field.NAME, current.name()),
        values.getOrDefault(Field.TAX_ID, current.taxId()),
        values.getOrDefault(Field.CONTACT, current.contact()),
        values.getOrDefault(Field.PHONE, current.phone()),
        values.getOrDefault(Field.EMAIL, current.email()));
  }
}
