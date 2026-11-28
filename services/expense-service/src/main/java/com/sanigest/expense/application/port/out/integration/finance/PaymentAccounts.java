package com.sanigest.expense.application.port.out.integration.finance;

import com.sanigest.events.domain.enums.payment.PaymentMethodV1;
import java.util.UUID;

public interface PaymentAccounts {
  void requireEligible(UUID company, UUID account, PaymentMethodV1 method);
}
