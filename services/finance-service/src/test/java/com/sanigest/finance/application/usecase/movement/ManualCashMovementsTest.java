package com.sanigest.finance.application.usecase.movement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sanigest.finance.application.dto.movement.ManualMovement.Account;
import com.sanigest.finance.application.dto.movement.ManualMovement.AccountOption;
import com.sanigest.finance.application.dto.movement.ManualMovement.Category;
import com.sanigest.finance.application.dto.movement.ManualMovement.Document;
import com.sanigest.finance.application.dto.movement.ManualMovement.Method;
import com.sanigest.finance.application.dto.movement.ManualMovement.Option;
import com.sanigest.finance.application.dto.movement.ManualMovement.Request;
import com.sanigest.finance.application.dto.movement.ManualMovement.Type;
import com.sanigest.finance.application.exception.movement.InvalidManualMovement;
import com.sanigest.finance.application.port.out.persistence.movement.ManualMovementStore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManualCashMovementsTest {
  private final UUID company = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final ManualMovementStore store = mock(ManualMovementStore.class);
  private final ManualCashMovements movements = new ManualCashMovements(store);
  private final UUID cash = UUID.randomUUID();
  private final UUID bank = UUID.randomUUID();

  @BeforeEach
  void accounts() {
    when(store.accounts(company))
        .thenReturn(
            List.of(
                new Account(cash, "Caja general", List.of(Method.CASH)),
                new Account(bank, "Banco Popular", List.of(Method.TRANSFER, Method.CHECK))));
  }

  @Test
  void publishesOrderedFormOptionsAndFiltersAccountSelector() {
    var options = movements.options(company);
    assertThat(options.movementTypes())
        .extracting(Option::value)
        .containsExactly("INCOME", "EXPENSE", "INTERNAL_TRANSFER");
    assertThat(options.movementMethods())
        .extracting(Option::label)
        .containsExactly("Efectivo", "Tarjeta", "Transferencia bancaria", "Cheque");
    assertThat(options.movementAccounts())
        .containsExactly(
            new Option(cash.toString(), "Caja general"),
            new Option(bank.toString(), "Banco Popular"));
    assertThat(movements.accountOptions(company, Method.TRANSFER, " POPULAR "))
        .containsExactly(new AccountOption(bank, "Banco Popular"));
    assertThat(movements.accountOptions(company, Method.CASH, "popular")).isEmpty();
  }

  @Test
  void validatesAmountAndTransferAccountsBeforeSaving() {
    assertInvalid(request(Type.INCOME, cash, null, "0.001"), "amount");
    assertInvalid(request(Type.INCOME, cash, null, "100000000000000000"), "amount");
    assertInvalid(request(Type.INCOME, UUID.randomUUID(), null, "10"), "destinationAccountId");
    assertInvalid(request(Type.INTERNAL_TRANSFER, cash, null, "10"), "sourceAccountId");
    assertInvalid(request(Type.INTERNAL_TRANSFER, cash, cash, "10"), "sourceAccountId");
    assertInvalid(request(Type.EXPENSE, cash, bank, "10"), "sourceAccountId");
  }

  @Test
  void usesTheStoredOperationIdAndServerActorForTheResult() {
    var request = request(Type.INTERNAL_TRANSFER, cash, bank, "10");
    UUID id = UUID.randomUUID();
    var document = new Document("application/pdf", new byte[] {1});
    when(store.save(eq(company), eq(actor), eq("key"), any(), eq(request), eq(document)))
        .thenReturn(id);
    var result = movements.register(company, actor, "key", request, document);
    assertThat(result.id()).isEqualTo(id);
    assertThat(result.actorId()).isEqualTo(actor);
    assertThat(result.origin()).isEqualTo("MANUAL");
    assertThat(result.hasDocument()).isTrue();
  }

  private void assertInvalid(Request request, String field) {
    assertThatThrownBy(() -> movements.register(company, actor, "key", request, null))
        .isInstanceOf(InvalidManualMovement.class)
        .hasMessageContaining(field);
  }

  private static Request request(Type type, UUID destination, UUID source, String amount) {
    return new Request(
        type,
        LocalDate.of(2026, 11, 28),
        Category.OTHER_INCOME,
        Method.CASH,
        "Entidad",
        new BigDecimal(amount),
        "Observación",
        "Concepto",
        destination,
        source,
        "REF");
  }
}
