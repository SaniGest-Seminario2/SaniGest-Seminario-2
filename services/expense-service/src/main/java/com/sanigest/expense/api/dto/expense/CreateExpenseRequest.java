package com.sanigest.expense.api.dto.expense;

import com.sanigest.expense.application.dto.expense.ExpenseRegistration.LineRequest;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.Mode;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.OperationType;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.ReceiptStatus;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.ReceiptType;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.Request;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.Tax;
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

public record CreateExpenseRequest(
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
    @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid LineBody> lines) {

  public Request toCommand() {
    return new Request(
        operationType,
        documentDate,
        receiptType,
        mode,
        supplierId,
        supplierInvoiceNumber,
        warehouseId,
        receiptStatus,
        ncf,
        attachmentId,
        notes,
        lines.stream().map(LineBody::toCommand).toList());
  }

  public record LineBody(
      @NotNull UUID itemId,
      @NotNull @Positive @Digits(integer = 12, fraction = 3) BigDecimal quantity,
      @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal unitCost,
      @NotNull Tax taxClassification,
      @PositiveOrZero @Digits(integer = 27, fraction = 2) BigDecimal discount,
      @PositiveOrZero @Digits(integer = 12, fraction = 3) BigDecimal receivedQuantity) {
    LineRequest toCommand() {
      return new LineRequest(
          itemId, quantity, unitCost, taxClassification, discount, receivedQuantity);
    }
  }
}
