package com.sanigest.expense.application.usecase;

import static com.sanigest.expense.application.exception.ExpenseFailure.invalid;

import com.sanigest.expense.application.dto.ExpenseRegistration.Document;
import com.sanigest.expense.application.dto.ExpenseRegistration.FinancialStatus;
import com.sanigest.expense.application.dto.ExpenseRegistration.Line;
import com.sanigest.expense.application.dto.ExpenseRegistration.LineRequest;
import com.sanigest.expense.application.dto.ExpenseRegistration.Mode;
import com.sanigest.expense.application.dto.ExpenseRegistration.ReceiptStatus;
import com.sanigest.expense.application.dto.ExpenseRegistration.RegistrationKey;
import com.sanigest.expense.application.dto.ExpenseRegistration.Request;
import com.sanigest.expense.application.exception.ExpenseFailure;
import com.sanigest.expense.application.port.out.integration.CatalogReferences;
import com.sanigest.expense.application.port.out.integration.InventoryReceipts;
import com.sanigest.expense.application.port.out.persistence.ExpenseStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class RegisterExpense {
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

  private final ExpenseStore store;
  private final CatalogReferences catalog;
  private final InventoryReceipts inventory;

  public RegisterExpense(
      ExpenseStore store, CatalogReferences catalog, InventoryReceipts inventory) {
    this.store = store;
    this.catalog = catalog;
    this.inventory = inventory;
  }

  public Document register(RegistrationKey key, String hash, Request request) {
    var stored =
        store.find(key).orElseGet(() -> store.save(key, hash, prepare(key.companyId(), request)));
    if (!stored.requestHash().equals(hash)) {
      throw new ExpenseFailure(ExpenseFailure.Kind.CONFLICT, "EXPENSE_IDEMPOTENCY_CONFLICT");
    }

    var document = stored.document();
    if (!stored.inventorySynced()) {
      for (var line : document.lines()) {
        if (line.stockTracked() && line.receivedQuantity().signum() > 0) {
          inventory.recordEntry(
              new InventoryReceipts.Entry(
                  document.companyId(),
                  document.id(),
                  line.id(),
                  document.warehouseId(),
                  line.itemId(),
                  line.receivedQuantity()));
        }
      }
      store.markInventorySynced(key, document.id());
    }
    return document;
  }

  private Document prepare(UUID company, Request request) {
    if (request.documentDate().getYear() < 1 || request.documentDate().getYear() > 9998) {
      throw invalid("EXPENSE_INVALID_DOCUMENT_DATE");
    }
    var supplier =
        catalog
            .findSupplier(company, request.supplierId())
            .filter(CatalogReferences.Supplier::active)
            .orElseThrow(() -> invalid("EXPENSE_INVALID_SUPPLIER"));
    var warehouse = request.warehouseId();
    if (warehouse != null
        && inventory
            .findWarehouse(company, warehouse)
            .filter(InventoryReceipts.Warehouse::active)
            .isEmpty()) {
      throw invalid("EXPENSE_INVALID_WAREHOUSE");
    }

    var dueDate = dueDate(request, supplier);
    var lines = prepareLines(company, request, warehouse);

    var receipt = receiptStatus(lines);
    if (request.receiptStatus() != receipt) {
      throw invalid("EXPENSE_INCONSISTENT_RECEIPT");
    }
    var total = sum(lines, Line::total);
    var financialStatus =
        total.signum() == 0
            ? FinancialStatus.NO_BALANCE
            : dueDate != null ? FinancialStatus.OPEN : FinancialStatus.PAYMENT_UNRECORDED;
    return new Document(
        UUID.randomUUID(),
        company,
        request.operationType(),
        request.documentDate(),
        request.receiptType(),
        request.mode(),
        supplier.id(),
        supplier.name(),
        request.supplierInvoiceNumber().strip(),
        warehouse,
        receipt,
        request.ncf(),
        request.attachmentId(),
        request.notes(),
        lines,
        sum(lines, Line::subtotal),
        sum(lines, Line::tax),
        total,
        dueDate,
        financialStatus);
  }

  private static LocalDate dueDate(Request request, CatalogReferences.Supplier supplier) {
    if (request.mode() == Mode.CASH) {
      if ("CREDIT".equals(supplier.paymentTerms())) {
        throw invalid("EXPENSE_UNSUPPORTED_PAYMENT_MODE");
      }
      return null;
    }
    if (!"CREDIT".equals(supplier.paymentTerms()) && !"BOTH".equals(supplier.paymentTerms())) {
      throw invalid("EXPENSE_UNSUPPORTED_PAYMENT_MODE");
    }
    if (supplier.creditDays() == null || supplier.creditDays() < 0) {
      throw invalid("EXPENSE_SUPPLIER_CREDIT_TERM_REQUIRED");
    }
    var due = request.documentDate().plusDays(supplier.creditDays());
    if (due.getYear() > 9999) {
      throw invalid("EXPENSE_INVALID_DUE_DATE");
    }
    return due;
  }

  private List<Line> prepareLines(UUID company, Request request, UUID warehouse) {
    var items = new HashMap<UUID, CatalogReferences.Item>();
    var tracking = new HashMap<UUID, Boolean>();
    return request.lines().stream()
        .map(input -> {
          var item =
              items.computeIfAbsent(
                  input.itemId(),
                  id ->
                      catalog
                          .findItem(company, id)
                          .filter(CatalogReferences.Item::active)
                          .filter(CatalogReferences.Item::purchasable)
                          .orElseThrow(() -> invalid("EXPENSE_INVALID_ITEM")));
          if (item.product() && warehouse == null) {
            throw invalid("EXPENSE_WAREHOUSE_REQUIRED");
          }
          boolean tracked =
              item.product()
                  && tracking.computeIfAbsent(
                      item.id(), id -> inventory.tracksStock(company, warehouse, id));
          return line(request.receiptStatus(), input, item, tracked);
        })
        .toList();
  }

  private static Line line(
      ReceiptStatus receipt, LineRequest input, CatalogReferences.Item item, boolean tracked) {
    var received = input.receivedQuantity();
    if (received == null) {
      received = receipt == ReceiptStatus.COMPLETE ? input.quantity() : BigDecimal.ZERO;
    }
    if (received.compareTo(input.quantity()) > 0
        || (!item.divisible()
            && item.product()
            && (input.quantity().stripTrailingZeros().scale() > 0
                || received.stripTrailingZeros().scale() > 0))) {
      throw invalid("EXPENSE_INVALID_QUANTITY");
    }
    var discount = input.discount() == null ? ZERO : input.discount();
    var gross = input.quantity().multiply(input.unitCost());
    if (discount.compareTo(gross) > 0) {
      throw invalid("EXPENSE_DISCOUNT_EXCEEDS_LINE_AMOUNT");
    }
    var subtotal = money(gross.subtract(discount));
    var tax = money(subtotal.multiply(input.taxClassification().rate()));
    return new Line(
        UUID.randomUUID(),
        item.id(),
        item.name(),
        input.quantity(),
        input.unitCost(),
        input.taxClassification(),
        discount,
        received,
        tracked,
        subtotal,
        tax,
        subtotal.add(tax),
        item.categoryId(),
        item.categoryName());
  }

  private static ReceiptStatus receiptStatus(List<Line> lines) {
    if (lines.stream().allMatch(line -> line.receivedQuantity().signum() == 0)) {
      return ReceiptStatus.PENDING;
    }
    if (lines.stream().allMatch(line -> line.receivedQuantity().compareTo(line.quantity()) == 0)) {
      return ReceiptStatus.COMPLETE;
    }
    return ReceiptStatus.PARTIAL;
  }

  private static BigDecimal sum(List<Line> lines, Function<Line, BigDecimal> amount) {
    return lines.stream().map(amount).reduce(ZERO, BigDecimal::add);
  }

  private static BigDecimal money(BigDecimal amount) {
    return amount.setScale(2, RoundingMode.HALF_UP);
  }
}
