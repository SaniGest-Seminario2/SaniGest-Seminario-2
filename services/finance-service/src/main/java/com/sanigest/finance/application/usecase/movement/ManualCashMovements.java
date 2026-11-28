package com.sanigest.finance.application.usecase.movement;

import com.sanigest.events.application.idempotency.FinancialWriteFingerprint;
import com.sanigest.finance.application.dto.movement.ManualMovement.Category;
import com.sanigest.finance.application.dto.movement.ManualMovement.Document;
import com.sanigest.finance.application.dto.movement.ManualMovement.Method;
import com.sanigest.finance.application.dto.movement.ManualMovement.Option;
import com.sanigest.finance.application.dto.movement.ManualMovement.Options;
import com.sanigest.finance.application.dto.movement.ManualMovement.Request;
import com.sanigest.finance.application.dto.movement.ManualMovement.Result;
import com.sanigest.finance.application.dto.movement.ManualMovement.Type;
import com.sanigest.finance.application.exception.movement.InvalidManualMovement;
import com.sanigest.finance.application.port.out.persistence.movement.ManualMovementStore;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ManualCashMovements {
  private enum Account {
    GENERAL_CASH("Caja general"),
    PETTY_CASH("Caja chica"),
    BANCO_POPULAR("Banco Popular"),
    BANRESERVAS("Banreservas"),
    CLEARING_CARD("Tarjeta por liquidar");

    private final String label;

    Account(String label) {
      this.label = label;
    }

    Option option(UUID company) {
      UUID id =
          UUID.nameUUIDFromBytes((company + ":finance:" + name()).getBytes(StandardCharsets.UTF_8));
      return new Option(id.toString(), label);
    }
  }

  private final ManualMovementStore store;

  public ManualCashMovements(ManualMovementStore store) {
    this.store = store;
  }

  public Options options(UUID company) {
    return new Options(
        List.of(
            option(Type.INCOME, "Ingreso"),
            option(Type.EXPENSE, "Egreso"),
            option(Type.INTERNAL_TRANSFER, "Transferencia interna")),
        List.of(
            option(Category.PARTNER_CONTRIBUTION, "Aportación de socio"),
            option(Category.LOAN_RECEIVED, "Préstamo recibido"),
            option(Category.BANK_INTEREST, "Intereses bancarios"),
            option(Category.OTHER_INCOME, "Otros ingresos")),
        List.of(
            option(Method.CASH, "Efectivo"),
            option(Method.CARD, "Tarjeta"),
            option(Method.TRANSFER, "Transferencia bancaria"),
            option(Method.CHECK, "Cheque")),
        Arrays.stream(Account.values()).map(account -> account.option(company)).toList());
  }

  public Result register(UUID company, UUID actor, String key, Request request, Document document) {
    validate(request);
    validateAccounts(company, request);
    String fingerprint =
        FinancialWriteFingerprint.v1(
            "manual-cash-movement",
            company,
            document == null ? List.of(request) : List.of(request, document));
    UUID id = store.save(company, actor, key, fingerprint, request, document);
    return new Result(id, "MANUAL", actor, request, document != null);
  }

  private static void validate(Request request) {
    required(request.type(), "type");
    required(request.date(), "date");
    if (request.date().getYear() < 1 || request.date().getYear() > 9999) {
      throw new InvalidManualMovement("date is outside the supported range");
    }
    required(request.category(), "category");
    required(request.method(), "method");
    required(request.personOrEntity(), "personOrEntity", 255);
    required(request.observations(), "observations", 2000);
    required(request.concept(), "concept", 255);
    required(request.bankReference(), "bankReference", 255);
    BigDecimal amount = request.amount();
    if (amount == null
        || amount.signum() <= 0
        || amount.stripTrailingZeros().scale() > 2
        || (long) amount.precision() - amount.scale() > 17) {
      throw new InvalidManualMovement(
          "amount must be positive with up to 17 integer and 2 decimal digits");
    }
  }

  private static void validateAccounts(UUID company, Request request) {
    requireAccount(company, request.destinationAccountId(), "destinationAccountId");
    if (request.type() == Type.INTERNAL_TRANSFER) {
      requireAccount(company, request.sourceAccountId(), "sourceAccountId");
      if (request.sourceAccountId().equals(request.destinationAccountId())) {
        throw new InvalidManualMovement("sourceAccountId must differ from destinationAccountId");
      }
    } else if (request.sourceAccountId() != null) {
      throw new InvalidManualMovement("sourceAccountId is only allowed for internal transfers");
    }
  }

  public Optional<Document> document(UUID company, UUID id) {
    return store.document(company, id);
  }

  private static Option option(Enum<?> value, String label) {
    return new Option(value.name(), label);
  }

  private static void requireAccount(UUID company, UUID id, String field) {
    required(id, field);
    if (Arrays.stream(Account.values())
        .noneMatch(account -> account.option(company).value().equals(id.toString()))) {
      throw new InvalidManualMovement(field + " is not a cash account of the selected company");
    }
  }

  private static void required(Object value, String field) {
    if (value == null) {
      throw new InvalidManualMovement(field + " is required");
    }
  }

  private static void required(String value, String field, int limit) {
    if (value == null || value.isBlank() || value.length() > limit || value.indexOf(0) >= 0) {
      throw new InvalidManualMovement(field + " must contain 1 to " + limit + " characters");
    }
  }
}
