package com.sanigest.finance.application.usecase.cash;

import com.sanigest.finance.application.dto.cash.CashSummary.Summary;
import com.sanigest.finance.application.port.in.cash.PaymentAdmission;
import com.sanigest.finance.application.port.out.integration.cash.PendingInvoices;
import com.sanigest.finance.application.port.out.persistence.cash.CashSummaryLedger;
import java.util.Optional;
import java.util.UUID;

public class ActiveCashSummary {
  private final PaymentAdmission admission;
  private final CashSummaryLedger movements;
  private final PendingInvoices pendingInvoices;

  public ActiveCashSummary(
      PaymentAdmission admission, CashSummaryLedger movements, PendingInvoices pendingInvoices) {
    this.admission = admission;
    this.movements = movements;
    this.pendingInvoices = pendingInvoices;
  }

  public Optional<Summary> find(UUID companyId, UUID operatorId, String accessToken) {
    // Sales checks company membership and SALES_READ before any shift information is returned.
    long pending = pendingInvoices.count(companyId, accessToken);
    return admission
        .find(companyId, operatorId)
        .map(shift -> {
          var amounts = movements.totals(companyId, shift.shiftId());
          return new Summary(
              shift.shiftId(),
              amounts.totalCollections(),
              pending,
              amounts.netCash(),
              amounts.otherMethods());
        });
  }
}
