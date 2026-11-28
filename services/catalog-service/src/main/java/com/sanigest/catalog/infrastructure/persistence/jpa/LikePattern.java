package com.sanigest.catalog.infrastructure.persistence.jpa;

public final class LikePattern {
  private LikePattern() {}

  public static String literalContains(String search) {
    return search == null || search.isBlank()
        ? ""
        : "%" + search.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }
}
