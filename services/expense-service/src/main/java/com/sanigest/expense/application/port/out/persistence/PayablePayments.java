package com.sanigest.expense.application.port.out.persistence;

import com.sanigest.expense.domain.model.PayablePayment.Details;
import com.sanigest.expense.domain.model.PayablePayment.Result;
import java.util.UUID;

public interface PayablePayments {
  Result register(UUID company, UUID actor, UUID payable, String key, Details payment);
}
