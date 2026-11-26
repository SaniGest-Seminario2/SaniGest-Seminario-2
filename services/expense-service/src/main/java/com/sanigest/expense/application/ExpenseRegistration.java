package com.sanigest.expense.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ExpenseRegistration {
  private ExpenseRegistration() {}

  public enum OperationType {
    PURCHASE,
    OPERATING_EXPENSE,
    ASSET_ACQUISITION
  }

  public enum ReceiptType {
    FISCAL_CREDIT,
    CONSUMPTION,
    PURCHASE,
    MINOR_EXPENSES,
    OTHER,
    NOT_APPLICABLE
  }

  public enum Mode {
    CASH,
    CREDIT
  }

  public enum ReceiptStatus {
    PENDING,
    COMPLETE,
    PARTIAL
  }

  public enum Tax {
    ITBIS_18("0.18"),
    ITBIS_16("0.16"),
    EXEMPT("0");

    private final BigDecimal rate;

    Tax(String rate) {
      this.rate = new BigDecimal(rate);
    }

    public BigDecimal rate() {
      return rate;
    }
  }

  public record Request(
      @NotNull OperationType operationType,
      @NotNull LocalDate documentDate,
      @NotNull ReceiptType receiptType,
      @NotNull Mode mode,
      @NotNull UUID supplierId,
      @NotBlank @Size(max = 100) String supplierInvoiceNumber,
      UUID warehouseId,
      @NotNull ReceiptStatus receiptStatus,
      @Size(max = 30) String ncf,
      UUID attachmentId,
      @Size(max = 1000) String notes,
      @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid LineRequest> lines) {}

  public record LineRequest(
      @NotNull UUID itemId,
      @NotNull @Positive @Digits(integer = 12, fraction = 3) BigDecimal quantity,
      @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal unitCost,
      @NotNull Tax taxClassification,
      @PositiveOrZero @Digits(integer = 27, fraction = 2) BigDecimal discount,
      @PositiveOrZero @Digits(integer = 12, fraction = 3) BigDecimal receivedQuantity) {}

  public record Document(
      UUID id,
      UUID companyId,
      OperationType operationType,
      LocalDate documentDate,
      ReceiptType receiptType,
      Mode mode,
      UUID supplierId,
      String supplierName,
      String supplierInvoiceNumber,
      UUID warehouseId,
      ReceiptStatus receiptStatus,
      String ncf,
      UUID attachmentId,
      String notes,
      List<Line> lines,
      BigDecimal subtotal,
      BigDecimal tax,
      BigDecimal total,
      LocalDate dueDate,
      FinancialStatus financialStatus) {}

  public enum FinancialStatus {
    OPEN,
    PAYMENT_UNRECORDED,
    NO_BALANCE
  }

  public record Line(
      UUID id,
      UUID itemId,
      String itemName,
      BigDecimal quantity,
      BigDecimal unitCost,
      Tax taxClassification,
      BigDecimal discount,
      BigDecimal receivedQuantity,
      boolean stockTracked,
      BigDecimal subtotal,
      BigDecimal tax,
      BigDecimal total) {}
}
