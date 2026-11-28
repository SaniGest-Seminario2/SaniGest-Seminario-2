package com.sanigest.sales.application.port.out.integration.finance;

import com.sanigest.sales.domain.model.payment.InvoicePayment;
import java.util.Map;
import java.util.UUID;

/** Finance authority for the operator's active shift and admitted destination accounts. */
public interface PaymentAdmission {
  Admission admit(UUID company, UUID operator, UUID payment, InvoicePayment.Request request);

  record Admission(UUID shiftId, Map<Integer, UUID> accountsByLine) {}
}
