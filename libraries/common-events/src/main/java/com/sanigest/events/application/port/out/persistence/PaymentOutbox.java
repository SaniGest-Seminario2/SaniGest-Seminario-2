package com.sanigest.events.application.port.out.persistence;

import com.sanigest.events.domain.model.payment.PaymentEventV1;
import java.util.UUID;

public interface PaymentOutbox {
  /** Enqueues a confirmed payment line or reversal inside the owner's payment transaction. */
  void enqueue(UUID companyId, PaymentEventV1 event);
}
