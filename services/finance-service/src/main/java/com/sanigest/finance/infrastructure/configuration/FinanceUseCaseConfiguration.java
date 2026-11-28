package com.sanigest.finance.infrastructure.configuration;

import com.sanigest.finance.application.port.in.cash.PaymentAdmission;
import com.sanigest.finance.application.port.out.integration.cash.PendingInvoices;
import com.sanigest.finance.application.port.out.integration.reporting.FinancialSnapshotSources;
import com.sanigest.finance.application.port.out.persistence.cash.CashSummaryLedger;
import com.sanigest.finance.application.port.out.persistence.movement.ManualMovementStore;
import com.sanigest.finance.application.port.out.persistence.reporting.FinancialProjectionStore;
import com.sanigest.finance.application.usecase.cash.ActiveCashSummary;
import com.sanigest.finance.application.usecase.movement.ManualCashMovements;
import com.sanigest.finance.application.usecase.reporting.FinancialProjections;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class FinanceUseCaseConfiguration {
  @Bean
  ManualCashMovements manualCashMovements(ManualMovementStore store) {
    return new ManualCashMovements(store);
  }

  @Bean
  ActiveCashSummary activeCashSummary(
      PaymentAdmission admission, CashSummaryLedger ledger, PendingInvoices invoices) {
    return new ActiveCashSummary(admission, ledger, invoices);
  }

  @Bean
  FinancialProjections financialProjections(
      FinancialProjectionStore store, FinancialSnapshotSources sources) {
    return new FinancialProjections(store, sources);
  }
}
