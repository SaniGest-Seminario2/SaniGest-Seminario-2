package com.sanigest.expense.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.expense.application.ExpenseRegistration.FinancialStatus;
import com.sanigest.expense.application.ExpenseRegistration.LineRequest;
import com.sanigest.expense.application.ExpenseRegistration.Mode;
import com.sanigest.expense.application.ExpenseRegistration.OperationType;
import com.sanigest.expense.application.ExpenseRegistration.ReceiptStatus;
import com.sanigest.expense.application.ExpenseRegistration.ReceiptType;
import com.sanigest.expense.application.ExpenseRegistration.Request;
import com.sanigest.expense.application.ExpenseRegistration.Tax;
import com.sanigest.expense.application.port.out.integration.CatalogReferences;
import com.sanigest.expense.application.port.out.integration.InventoryReceipts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegisterExpenseTest {
  private static final LocalDate DATE = LocalDate.of(2026, 11, 27);
  private final ExpenseStore.Key key =
      new ExpenseStore.Key(UUID.randomUUID(), UUID.randomUUID(), "key");
  private final UUID supplier = UUID.randomUUID();
  private final UUID warehouse = UUID.randomUUID();
  private final UUID item = UUID.randomUUID();
  private final ExpenseStore store = mock();
  private final CatalogReferences catalog = mock();
  private final InventoryReceipts inventory = mock();
  private final RegisterExpense register = new RegisterExpense(store, catalog, inventory);

  @BeforeEach
  void prepareReferences() {
    supplier("BOTH", 30);
    when(catalog.findItem(key.companyId(), item))
        .thenReturn(
            Optional.of(
                new CatalogReferences.Item(item, "Item", true, true, true, false, null, null)));
    when(inventory.findWarehouse(key.companyId(), warehouse))
        .thenReturn(Optional.of(new InventoryReceipts.Warehouse(warehouse, true)));
    when(inventory.tracksStock(key.companyId(), warehouse, item)).thenReturn(true);
    when(store.save(eq(key), eq("hash"), any()))
        .thenAnswer(call -> new ExpenseStore.Stored("hash", call.getArgument(2), false));
  }

  @Test
  void appliesDiscountAndTaxAndRecordsOnlyReceivedQuantity() {
    var request = request(DATE, Mode.CREDIT, warehouse, ReceiptStatus.PARTIAL, line("3", "5", "2"));
    var document = register.register(key, "hash", request);
    assertEquals(DATE.plusDays(30), document.dueDate());
    assertEquals(FinancialStatus.OPEN, document.financialStatus());
    assertEquals(new BigDecimal("25.00"), document.subtotal());
    assertEquals(new BigDecimal("4.50"), document.tax());
    assertEquals(new BigDecimal("29.50"), document.total());
    verify(inventory)
        .recordEntry(
            new InventoryReceipts.Entry(
                key.companyId(),
                document.lines().getFirst().id(),
                warehouse,
                item,
                new BigDecimal("2")));
    verify(store).markInventorySynced(key, document.id());

    clearInvocations(inventory, catalog, store);
    when(store.find(key)).thenReturn(Optional.of(new ExpenseStore.Stored("hash", document, true)));
    assertEquals(document, register.register(key, "hash", request));
    assertEquals(
        "EXPENSE_IDEMPOTENCY_CONFLICT",
        assertThrows(ExpenseFailure.class, () -> register.register(key, "different", request))
            .getMessage());
    verifyNoInteractions(inventory, catalog);
    verify(store, never()).markInventorySynced(any(), any());
  }

  @Test
  void derivesCashAndZeroBalanceStatusesAndSkipsPendingStock() {
    var cash =
        register.register(
            key,
            "hash",
            request(DATE, Mode.CASH, warehouse, ReceiptStatus.PENDING, line("2", null, null)));
    assertEquals(FinancialStatus.PAYMENT_UNRECORDED, cash.financialStatus());
    assertNull(cash.dueDate());
    verify(inventory, never()).recordEntry(any());
    var zero =
        register.register(
            key,
            "hash",
            request(DATE, Mode.CREDIT, warehouse, ReceiptStatus.COMPLETE, line("1", "10", null)));
    assertEquals(FinancialStatus.NO_BALANCE, zero.financialStatus());
    assertEquals(BigDecimal.ONE, zero.lines().getFirst().receivedQuantity());
  }

  @Test
  void rejectsInvalidReceiptAndLineAmountsBeforeSaving() {
    invalid(
        "EXPENSE_INVALID_QUANTITY",
        Mode.CREDIT,
        warehouse,
        ReceiptStatus.PARTIAL,
        line("2", null, "3"));
    invalid(
        "EXPENSE_INVALID_QUANTITY",
        Mode.CREDIT,
        warehouse,
        ReceiptStatus.PENDING,
        line("1.5", null, null));
    invalid(
        "EXPENSE_DISCOUNT_EXCEEDS_LINE_AMOUNT",
        Mode.CREDIT,
        warehouse,
        ReceiptStatus.PENDING,
        line("1", "11", null));
    invalid(
        "EXPENSE_INCONSISTENT_RECEIPT",
        Mode.CREDIT,
        warehouse,
        ReceiptStatus.PARTIAL,
        line("1", null, null));
    invalid(
        "EXPENSE_WAREHOUSE_REQUIRED",
        Mode.CREDIT,
        null,
        ReceiptStatus.PENDING,
        line("1", null, null));
    verify(store, never()).save(any(), any(), any());
  }

  @Test
  void rejectsIneligibleSuppliersAndPaymentTerms() {
    invalid(
        "EXPENSE_INVALID_WAREHOUSE",
        Mode.CREDIT,
        UUID.randomUUID(),
        ReceiptStatus.PENDING,
        line("1", null, null));
    supplier("CASH", null);
    invalid(
        "EXPENSE_UNSUPPORTED_PAYMENT_MODE",
        Mode.CREDIT,
        warehouse,
        ReceiptStatus.PENDING,
        line("1", null, null));
    supplier("CREDIT", null);
    invalid(
        "EXPENSE_UNSUPPORTED_PAYMENT_MODE",
        Mode.CASH,
        warehouse,
        ReceiptStatus.PENDING,
        line("1", null, null));
    invalid(
        "EXPENSE_SUPPLIER_CREDIT_TERM_REQUIRED",
        Mode.CREDIT,
        warehouse,
        ReceiptStatus.PENDING,
        line("1", null, null));
    when(catalog.findSupplier(key.companyId(), supplier)).thenReturn(Optional.empty());
    invalid(
        "EXPENSE_INVALID_SUPPLIER",
        Mode.CREDIT,
        warehouse,
        ReceiptStatus.PENDING,
        line("1", null, null));
  }

  private void supplier(String terms, Integer days) {
    when(catalog.findSupplier(key.companyId(), supplier))
        .thenReturn(
            Optional.of(new CatalogReferences.Supplier(supplier, "Supplier", true, terms, days)));
  }

  private void invalid(
      String code, Mode mode, UUID location, ReceiptStatus receipt, LineRequest line) {
    var input = request(DATE, mode, location, receipt, line);
    assertEquals(
        code,
        assertThrows(ExpenseFailure.class, () -> register.register(key, "hash", input))
            .getMessage());
  }

  private Request request(
      LocalDate date, Mode mode, UUID location, ReceiptStatus receipt, LineRequest line) {
    return new Request(
        OperationType.PURCHASE,
        date,
        ReceiptType.PURCHASE,
        mode,
        supplier,
        " INV-1 ",
        location,
        receipt,
        null,
        null,
        null,
        List.of(line));
  }

  private LineRequest line(String quantity, String discount, String received) {
    return new LineRequest(
        item,
        new BigDecimal(quantity),
        BigDecimal.TEN,
        Tax.ITBIS_18,
        discount == null ? null : new BigDecimal(discount),
        received == null ? null : new BigDecimal(received));
  }
}
