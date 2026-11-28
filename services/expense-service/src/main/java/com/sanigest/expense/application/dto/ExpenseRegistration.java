package com.sanigest.expense.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ExpenseRegistration {
  public record RegistrationKey(UUID companyId, UUID actorId, String requestKey) {}

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
      OperationType operationType,
      LocalDate documentDate,
      ReceiptType receiptType,
      Mode mode,
      UUID supplierId,
      String supplierInvoiceNumber,
      UUID warehouseId,
      ReceiptStatus receiptStatus,
      String ncf,
      UUID attachmentId,
      String notes,
      List<LineRequest> lines) {}

  public record LineRequest(
      UUID itemId,
      BigDecimal quantity,
      BigDecimal unitCost,
      Tax taxClassification,
      BigDecimal discount,
      BigDecimal receivedQuantity) {}

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
      BigDecimal total,
      UUID categoryId,
      String categoryName) {}
}
