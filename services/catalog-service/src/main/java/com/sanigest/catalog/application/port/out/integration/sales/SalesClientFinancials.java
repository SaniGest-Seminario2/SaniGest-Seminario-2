package com.sanigest.catalog.application.port.out.integration.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SalesClientFinancials {
  Map<UUID, BigDecimal> balances(UUID company, List<UUID> ids);

  CompanyTotals summary(UUID company);

  Totals totals(UUID company, UUID clientId);

  Statement statement(UUID company, UUID clientId, int page, int size);

  record CompanyTotals(long clientsWithPendingBalance, BigDecimal totalPending, Instant asOf) {}

  record Totals(
      BigDecimal balance,
      BigDecimal creditExposure,
      BigDecimal totalInvoiced,
      BigDecimal paymentsReceived,
      Instant asOf) {}

  record Invoice(UUID id, LocalDate date, BigDecimal amount, String status) {}

  record Payment(UUID id, UUID invoiceId, Instant date, BigDecimal amount) {}

  record Statement(
      List<Invoice> invoices,
      List<Payment> payments,
      int page,
      int size,
      boolean hasMoreInvoices,
      boolean hasMorePayments,
      Instant asOf) {}
}
