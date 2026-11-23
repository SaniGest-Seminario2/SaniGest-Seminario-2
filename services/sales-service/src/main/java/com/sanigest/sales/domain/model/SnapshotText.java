package com.sanigest.sales.domain.model;

final class SnapshotText {
  private SnapshotText() {}

  static String name(String value, int limit, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return bounded(value, limit, field);
  }

  static String bounded(String value, int limit, String field) {
    if (value != null
        && (value.codePointCount(0, value.length()) > limit || value.indexOf(0) >= 0)) {
      throw new IllegalArgumentException(field + " contains invalid text");
    }
    return value;
  }
}
