package com.sanigest.sales.application.usecase.invoice;

import com.sanigest.sales.application.port.out.integration.ActiveCashShifts;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission;
import com.sanigest.sales.application.port.out.persistence.InvoicePayments;
import com.sanigest.sales.domain.model.InvoicePayment;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class ListActiveShiftPayments {
  private final SalesAuthorization authorization;
  private final Supplier<UUID> actor;
  private final ActiveCashShifts shifts;
  private final InvoicePayments payments;

  public ListActiveShiftPayments(
      SalesAuthorization authorization,
      Supplier<UUID> actor,
      ActiveCashShifts shifts,
      InvoicePayments payments) {
    this.authorization = authorization;
    this.actor = actor;
    this.shifts = shifts;
    this.payments = payments;
  }

  public List<InvoicePayment.HistoryLine> execute() {
    var company = authorization.requireMembership(Permission.SALES_READ);
    var operator = actor.get();
    return shifts
        .find(company, operator)
        .map(shift -> payments.findByShift(company, operator, shift.shiftId()))
        .orElseGet(List::of);
  }
}
