package com.sanigest.inventory.api.dto;

public record ValueOption<T>(T value, String label) {
  public static ValueOption<String> of(Enum<?> value, String label) {
    return new ValueOption<>(value.name(), label);
  }
}
