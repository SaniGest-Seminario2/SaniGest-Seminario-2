package com.sanigest.finance.application.dto.movement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ManualMovement {
  private ManualMovement() {}

  public enum Type {
    INCOME,
    EXPENSE,
    INTERNAL_TRANSFER
  }

  public enum Category {
    PARTNER_CONTRIBUTION,
    LOAN_RECEIVED,
    BANK_INTEREST,
    OTHER_INCOME
  }

  public enum Method {
    CASH,
    CARD,
    TRANSFER,
    CHECK
  }

  public record Request(
      Type type,
      LocalDate date,
      Category category,
      Method method,
      String personOrEntity,
      BigDecimal amount,
      String observations,
      String concept,
      UUID destinationAccountId,
      UUID sourceAccountId,
      String bankReference) {}

  public record Result(
      UUID id, String origin, UUID actorId, Request movement, boolean hasDocument) {}

  public record Document(String contentType, byte[] content) {}

  public record Option(String value, String label) {}

  public record Options(
      List<Option> movementTypes,
      List<Option> movementCategories,
      List<Option> movementMethods,
      List<Option> movementAccounts) {}
}
