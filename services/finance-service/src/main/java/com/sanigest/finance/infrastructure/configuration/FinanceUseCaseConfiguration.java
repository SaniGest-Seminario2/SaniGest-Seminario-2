package com.sanigest.finance.infrastructure.configuration;

import com.sanigest.finance.application.port.in.PaymentAdmission;
import com.sanigest.finance.application.port.out.integration.FinancialSnapshotSources;
import com.sanigest.finance.application.port.out.integration.PendingInvoices;
import com.sanigest.finance.application.port.out.persistence.CashSummaryLedger;
import com.sanigest.finance.application.port.out.persistence.FinancialProjectionStore;
import com.sanigest.finance.application.port.out.persistence.ManualMovementStore;
import com.sanigest.finance.application.usecase.ActiveCashSummary;
import com.sanigest.finance.application.usecase.FinancialProjections;
import com.sanigest.finance.application.usecase.ManualCashMovements;
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
