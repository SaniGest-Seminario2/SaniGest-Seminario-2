package com.sanigest.catalog.infrastructure.persistence.jpa;

final class LikePattern {
  private LikePattern() {}

  static String literalContains(String search) {
    return search == null || search.isBlank()
        ? ""
        : "%" + search.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }
}
