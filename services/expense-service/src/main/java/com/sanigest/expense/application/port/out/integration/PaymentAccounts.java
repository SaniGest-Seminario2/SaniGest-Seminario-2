package com.sanigest.expense.application.port.out.integration;

import com.sanigest.events.payment.PaymentMethodV1;
import java.util.UUID;

public interface PaymentAccounts {
  void requireEligible(UUID company, UUID account, PaymentMethodV1 method);
}
