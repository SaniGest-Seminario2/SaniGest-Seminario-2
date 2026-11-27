package com.sanigest.events.outbox;

import com.sanigest.events.payment.PaymentEventV1;
import java.util.UUID;

public interface PaymentOutbox {
  /** Enqueues a confirmed payment line or reversal inside the owner's payment transaction. */
  void enqueue(UUID companyId, PaymentEventV1 event);
}
