package com.sanigest.company.domain.model.advisor;

import java.util.Objects;

public record AdvisorPermission(Module module, Scope scope) {
  public AdvisorPermission {
    Objects.requireNonNull(module);
    Objects.requireNonNull(scope);
  }

  public enum Module {
    CATALOG,
    SALES,
    EXPENSE,
    FINANCE
  }

  public enum Scope {
    READ,
    WRITE
  }
}
