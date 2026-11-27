package com.sanigest.finance.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActiveCashSummaryTest {
  @Test
  void verifiesAccessThenCombinesShiftMovementsWithPendingInvoices() {
    var company = UUID.randomUUID();
    var operator = UUID.randomUUID();
    var shift = UUID.randomUUID();
    var invoices = mock(ActiveCashSummary.PendingInvoices.class);
    var admission = mock(PaymentAdmission.class);
    var movements = mock(ActiveCashSummary.Movements.class);
    var methods = Map.of("TRANSFER", BigDecimal.TEN);
    when(invoices.count(company, "token")).thenReturn(2L);
    when(admission.find(company, operator))
        .thenReturn(Optional.of(new PaymentAdmission.ActiveShift(shift, Map.of())));
    when(movements.totals(company, shift))
        .thenReturn(new ActiveCashSummary.Amounts(BigDecimal.TEN, BigDecimal.ZERO, methods));

    assertEquals(
        new ActiveCashSummary.Summary(shift, BigDecimal.TEN, 2, BigDecimal.ZERO, methods),
        new ActiveCashSummary(admission, movements, invoices)
            .find(company, operator, "token")
            .orElseThrow());
    var order = inOrder(invoices, admission, movements);
    order.verify(invoices).count(company, "token");
    order.verify(admission).find(company, operator);
    order.verify(movements).totals(company, shift);
  }
}
