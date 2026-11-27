package com.sanigest.sales.application.port.out.persistence;

import com.sanigest.sales.application.port.out.integration.PaymentAdmission;
import com.sanigest.sales.domain.model.InvoicePayment;
import java.util.UUID;

public interface InvoicePayments {
  InvoicePayment.Result register(
      UUID company,
      UUID operator,
      UUID invoice,
      String key,
      InvoicePayment.Request request,
      PaymentAdmission admission);
}
