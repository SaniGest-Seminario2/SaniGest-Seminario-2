package com.sanigest.security;

import java.util.List;
import java.util.UUID;

/** Parses a requested company without authorizing access to it. */
public final class CompanySelector {
  public static final String HEADER = "X-Company-Id";

  private CompanySelector() {}

  public static UUID resolve(List<String> values, String pathCompany, boolean required) {
    UUID selected = null;
    if (!values.isEmpty()) {
      if (values.size() != 1) {
        throw new InvalidContext("INVALID_COMPANY_CONTEXT");
      }
      selected = parse(values.getFirst());
    }
    if (pathCompany != null) {
      UUID path = parse(pathCompany);
      if (selected != null && !selected.equals(path)) {
        throw new InvalidContext("COMPANY_CONTEXT_MISMATCH");
      }
      return path;
    }
    if (required && selected == null) {
      throw new InvalidContext("COMPANY_CONTEXT_REQUIRED");
    }
    return selected;
  }

  private static UUID parse(String value) {
    if (!AccessTokens.validSubject(value)) {
      throw new InvalidContext("INVALID_COMPANY_CONTEXT");
    }
    return UUID.fromString(value);
  }

  public static final class InvalidContext extends RuntimeException {
    public InvalidContext(String code) {
      super(code);
    }
  }
}
