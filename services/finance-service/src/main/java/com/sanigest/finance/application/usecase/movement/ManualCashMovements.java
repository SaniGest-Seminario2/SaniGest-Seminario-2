package com.sanigest.finance.application.usecase.movement;

import com.sanigest.events.application.idempotency.FinancialWriteFingerprint;
import com.sanigest.finance.application.dto.movement.ManualMovement.Account;
import com.sanigest.finance.application.dto.movement.ManualMovement.AccountBalance;
import com.sanigest.finance.application.dto.movement.ManualMovement.AccountOption;
import com.sanigest.finance.application.dto.movement.ManualMovement.Category;
import com.sanigest.finance.application.dto.movement.ManualMovement.Document;
import com.sanigest.finance.application.dto.movement.ManualMovement.Filters;
import com.sanigest.finance.application.dto.movement.ManualMovement.Method;
import com.sanigest.finance.application.dto.movement.ManualMovement.Option;
import com.sanigest.finance.application.dto.movement.ManualMovement.Options;
import com.sanigest.finance.application.dto.movement.ManualMovement.Request;
import com.sanigest.finance.application.dto.movement.ManualMovement.Result;
import com.sanigest.finance.application.dto.movement.ManualMovement.Type;
import com.sanigest.finance.application.exception.movement.InvalidManualMovement;
import com.sanigest.finance.application.port.in.movement.CashMovementQueries.Origin;
import com.sanigest.finance.application.port.out.persistence.movement.ManualMovementStore;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class ManualCashMovements {
  private final ManualMovementStore store;

  public ManualCashMovements(ManualMovementStore store) {
    this.store = store;
  }

  public Options options(UUID company) {
    var types =
        List.of(
            option(Type.INCOME, "Ingreso"),
            option(Type.EXPENSE, "Egreso"),
            option(Type.INTERNAL_TRANSFER, "Transferencia interna"));
    var categories =
        List.of(
            option(Category.PARTNER_CONTRIBUTION, "Aportación de socio"),
            option(Category.LOAN_RECEIVED, "Préstamo recibido"),
            option(Category.BANK_INTEREST, "Intereses bancarios"),
            option(Category.OTHER_INCOME, "Otros ingresos"));
    var methods =
        List.of(
            option(Method.CASH, "Efectivo"),
            option(Method.CARD, "Tarjeta"),
            option(Method.TRANSFER, "Transferencia bancaria"),
            option(Method.CHECK, "Cheque"));
    var accounts =
        store.accounts(company).stream()
            .map(account -> new Option(account.id().toString(), account.name()))
            .toList();
    var filters =
        new Filters(
            withAll("Todos", types),
            List.of(
                new Option("ALL", "Todos"),
                option(Origin.CASH_SESSION, "Caja"),
                option(Origin.RECEIVABLE_PAYMENT, "Cobro"),
                option(Origin.PAYABLE_PAYMENT, "Cuenta por pagar"),
                option(Origin.MANUAL, "Manual")),
            withAll("Todos", methods),
            Stream.concat(
                    Stream.of(
                        new Option("ALL", "Todas"),
                        new Option("SALES", "Ventas"),
                        new Option("SUPPLIES", "Suministros")),
                    categories.stream())
                .toList());
    return new Options(types, categories, methods, accounts, filters);
  }

  private static List<Option> withAll(String label, List<Option> options) {
    return Stream.concat(Stream.of(new Option("ALL", label)), options.stream()).toList();
  }

  public List<AccountOption> accountOptions(UUID company, Method method, String search) {
    String term = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
    return store.accounts(company).stream()
        .filter(account -> method == null || account.methods().contains(method))
        .filter(account -> account.name().toLowerCase(Locale.ROOT).contains(term))
        .map(account -> new AccountOption(account.id(), account.name()))
        .toList();
  }

  public List<AccountBalance> balances(UUID company) {
    return store.balances(company);
  }

  public Result register(UUID company, UUID actor, String key, Request request, Document document) {
    validate(request);
    validateAccounts(store.accounts(company), request);
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

  private static void validateAccounts(List<Account> accounts, Request request) {
    requireAccount(accounts, request.destinationAccountId(), "destinationAccountId");
    if (request.type() == Type.INTERNAL_TRANSFER) {
      requireAccount(accounts, request.sourceAccountId(), "sourceAccountId");
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

  private static void requireAccount(List<Account> accounts, UUID id, String field) {
    required(id, field);
    if (accounts.stream().noneMatch(account -> account.id().equals(id))) {
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
