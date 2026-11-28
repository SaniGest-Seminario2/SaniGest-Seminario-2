package com.sanigest.expense.application.usecase;

import com.sanigest.events.payment.PaymentMethodV1;
import com.sanigest.expense.application.exception.ExpenseFailure;
import com.sanigest.expense.application.port.out.persistence.PayablePayments;
import com.sanigest.expense.domain.model.PayablePayment;
import com.sanigest.expense.domain.model.PayablePayment.Details;
import com.sanigest.expense.domain.model.PayablePayment.Result;
import java.time.LocalDate;
import java.util.UUID;

public class RegisterPayablePayment {
  private final PayablePayments payments;

  public RegisterPayablePayment(PayablePayments payments) {
    this.payments = payments;
  }

  public Result register(UUID company, UUID actor, UUID payable, String key, Details payment) {
    String reference = normalize(payment.reference());
    String notes = normalize(payment.notes());
    if ((payment.method() == PaymentMethodV1.CASH) != (reference == null)) {
      throw ExpenseFailure.invalid("PAYABLE_INVALID_PAYMENT_REFERENCE");
    }
    if (payment.date().getYear() < 1
        || payment.date().isAfter(LocalDate.now(PayablePayment.BUSINESS_ZONE))) {
      throw ExpenseFailure.invalid("PAYABLE_INVALID_PAYMENT_DATE");
    }
    return payments.register(
        company,
        actor,
        payable,
        key,
        new Details(
            payment.amount().setScale(2),
            payment.method(),
            payment.accountId(),
            payment.date(),
            reference,
            notes));
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    if (value.indexOf(0) >= 0) {
      throw ExpenseFailure.invalid("PAYABLE_INVALID_PAYMENT_TEXT");
    }
    return value.strip();
  }
}
