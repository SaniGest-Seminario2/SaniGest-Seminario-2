package com.sanigest.expense.api.dto.payable;

import com.sanigest.events.domain.enums.payment.PaymentMethodV1;
import com.sanigest.expense.domain.model.payable.PayablePayment.Details;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PayablePaymentRequest(
    @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
    @NotNull PaymentMethodV1 method,
    @NotNull UUID accountId,
    @NotNull LocalDate date,
    @Size(max = 250) String reference,
    @Size(max = 500) String notes) {
  public Details details() {
    return new Details(amount, method, accountId, date, reference, notes);
  }
}
