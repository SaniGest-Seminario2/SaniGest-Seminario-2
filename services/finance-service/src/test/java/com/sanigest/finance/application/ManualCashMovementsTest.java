package com.sanigest.finance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sanigest.finance.application.ManualCashMovements.Category;
import com.sanigest.finance.application.ManualCashMovements.Invalid;
import com.sanigest.finance.application.ManualCashMovements.Method;
import com.sanigest.finance.application.ManualCashMovements.Request;
import com.sanigest.finance.application.ManualCashMovements.Store;
import com.sanigest.finance.application.ManualCashMovements.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManualCashMovementsTest {
  private final UUID company = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final Store store = mock(Store.class);
  private final ManualCashMovements movements = new ManualCashMovements(store);
  private final UUID cash = account(company, 0);

  @Test
  void publishesOrderedFormOptionsAndScopesAccountIdsToCompany() {
    var options = movements.options(company);
    assertThat(options.movementTypes())
        .extracting(ManualCashMovements.Option::value)
        .containsExactly("INCOME", "EXPENSE", "INTERNAL_TRANSFER");
    assertThat(options.movementMethods())
        .extracting(ManualCashMovements.Option::label)
        .containsExactly("Efectivo", "Tarjeta", "Transferencia bancaria", "Cheque");
    assertThat(cash).isEqualTo(account(company, 0)).isNotEqualTo(account(UUID.randomUUID(), 0));
  }

  @Test
  void validatesAmountAndTransferAccountsBeforeSaving() {
    assertInvalid(request(Type.INCOME, cash, null, "0.001"), "amount");
    assertInvalid(request(Type.INCOME, cash, null, "100000000000000000"), "amount");
    assertInvalid(
        request(Type.INCOME, account(UUID.randomUUID(), 0), null, "10"), "destinationAccountId");
    assertInvalid(request(Type.INTERNAL_TRANSFER, cash, null, "10"), "sourceAccountId");
    assertInvalid(request(Type.INTERNAL_TRANSFER, cash, cash, "10"), "sourceAccountId");
    assertInvalid(request(Type.EXPENSE, cash, account(company, 1), "10"), "sourceAccountId");
  }

  @Test
  void usesTheStoredOperationIdAndServerActorForTheResult() {
    var request = request(Type.INTERNAL_TRANSFER, cash, account(company, 1), "10");
    UUID id = UUID.randomUUID();
    var document = new ManualCashMovements.Document("application/pdf", new byte[] {1});
    when(store.save(eq(company), eq(actor), eq("key"), any(), eq(request), eq(document)))
        .thenReturn(id);
    var result = movements.register(company, actor, "key", request, document);
    assertThat(result.id()).isEqualTo(id);
    assertThat(result.actorId()).isEqualTo(actor);
    assertThat(result.origin()).isEqualTo("MANUAL");
    assertThat(result.documentUrl()).endsWith(id + "/attachment");
  }

  private void assertInvalid(Request request, String field) {
    assertThatThrownBy(() -> movements.register(company, actor, "key", request, null))
        .isInstanceOf(Invalid.class)
        .hasMessageContaining(field);
  }

  private UUID account(UUID companyId, int index) {
    return UUID.fromString(movements.options(companyId).movementAccounts().get(index).value());
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
