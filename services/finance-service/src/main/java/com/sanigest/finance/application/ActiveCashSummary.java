package com.sanigest.finance.application;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ActiveCashSummary {
  private final PaymentAdmission admission;
  private final Movements movements;
  private final PendingInvoices pendingInvoices;

  ActiveCashSummary(
      PaymentAdmission admission, Movements movements, PendingInvoices pendingInvoices) {
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

  public interface Movements {
    Amounts totals(UUID companyId, UUID shiftId);
  }

  public interface PendingInvoices {
    long count(UUID companyId, String accessToken);
  }

  public record Amounts(
      BigDecimal totalCollections, BigDecimal netCash, Map<String, BigDecimal> otherMethods) {}

  public record Summary(
      UUID shiftId,
      BigDecimal totalCollections,
      long pendingInvoices,
      BigDecimal netCash,
      Map<String, BigDecimal> otherMethods) {}
}
