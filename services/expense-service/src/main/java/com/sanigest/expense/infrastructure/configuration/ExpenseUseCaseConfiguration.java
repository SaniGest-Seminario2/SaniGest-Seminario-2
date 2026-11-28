package com.sanigest.expense.infrastructure.configuration;

import com.sanigest.expense.application.port.out.integration.catalog.CatalogReferences;
import com.sanigest.expense.application.port.out.integration.inventory.InventoryReceipts;
import com.sanigest.expense.application.port.out.persistence.expense.ExpenseStore;
import com.sanigest.expense.application.port.out.persistence.payable.PayablePayments;
import com.sanigest.expense.application.usecase.expense.RegisterExpense;
import com.sanigest.expense.application.usecase.payable.RegisterPayablePayment;
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
