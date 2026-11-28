package com.sanigest.expense.infrastructure.configuration;

import com.sanigest.expense.application.port.out.integration.CatalogReferences;
import com.sanigest.expense.application.port.out.integration.InventoryReceipts;
import com.sanigest.expense.application.port.out.persistence.ExpenseStore;
import com.sanigest.expense.application.port.out.persistence.PayablePayments;
import com.sanigest.expense.application.usecase.RegisterExpense;
import com.sanigest.expense.application.usecase.RegisterPayablePayment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ExpenseUseCaseConfiguration {
  @Bean
  RegisterExpense registerExpense(
      ExpenseStore store, CatalogReferences catalog, InventoryReceipts inventory) {
    return new RegisterExpense(store, catalog, inventory);
  }

  @Bean
  RegisterPayablePayment registerPayablePayment(PayablePayments payments) {
    return new RegisterPayablePayment(payments);
  }
}
