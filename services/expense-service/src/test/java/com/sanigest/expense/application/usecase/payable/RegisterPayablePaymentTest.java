package com.sanigest.expense.application.usecase.payable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.sanigest.events.domain.enums.payment.PaymentMethodV1;
import com.sanigest.expense.application.exception.ExpenseFailure;
import com.sanigest.expense.domain.model.payable.PayablePayment;
import com.sanigest.expense.domain.model.payable.PayablePayment.Details;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegisterPayablePaymentTest {
  private final UUID id = UUID.randomUUID();
  private final LocalDate today = LocalDate.now(PayablePayment.BUSINESS_ZONE);

  @Test
  void normalizesValuesBeforeCheckingIdempotency() {
    var useCase =
        new RegisterPayablePayment((company, actor, payable, key, payment) -> {
          assertEquals(
              new Details(
                  new BigDecimal("10.00"), PaymentMethodV1.TRANSFER, id, today, "REF", null),
              payment);
          return null;
        });
    useCase.register(
        id,
        id,
        id,
        "key",
        new Details(BigDecimal.TEN, PaymentMethodV1.TRANSFER, id, today, " REF ", " "));
  }

  @Test
  void rejectsIneligibleReferencesAndFutureDatesBeforePersistence() {
    assertRejected(PaymentMethodV1.CASH, "REF", today, "PAYABLE_INVALID_PAYMENT_REFERENCE");
    assertRejected(PaymentMethodV1.TRANSFER, " ", today, "PAYABLE_INVALID_PAYMENT_REFERENCE");
    assertRejected(PaymentMethodV1.CASH, null, today.plusDays(2), "PAYABLE_INVALID_PAYMENT_DATE");
  }

  private void assertRejected(
      PaymentMethodV1 method, String reference, LocalDate date, String code) {
    var useCase =
        new RegisterPayablePayment(
            (company, actor, payable, key, payment) -> fail("Invalid payment reached persistence"));
    var failure =
        assertThrows(
            ExpenseFailure.class,
            () ->
                useCase.register(
                    id,
                    id,
                    id,
                    "key",
                    new Details(BigDecimal.TEN, method, id, date, reference, null)));
    assertEquals(code, failure.getMessage());
  }
}
