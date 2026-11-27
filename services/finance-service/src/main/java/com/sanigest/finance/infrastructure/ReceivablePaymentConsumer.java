package com.sanigest.finance.infrastructure;

import com.sanigest.events.payment.PaymentEventTypeV1;
import com.sanigest.events.payment.PaymentEventV1;
import java.math.RoundingMode;
import java.sql.Timestamp;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class ReceivablePaymentConsumer {
  private final ObjectMapper json;
  private final JdbcClient jdbc;

  ReceivablePaymentConsumer(ObjectMapper json, JdbcClient jdbc) {
    this.json = json;
    this.jdbc = jdbc;
  }

  @KafkaListener(topics = "sales.receivable-payment.v1")
  void project(ConsumerRecord<String, String> record) {
    var event = json.readValue(record.value(), PaymentEventV1.class);
    if (event.eventType() != PaymentEventTypeV1.ReceivablePaymentRegisteredV1
        || !event.companyId().toString().equals(record.key())
        || event.shiftId() == null) {
      throw new IllegalArgumentException("Invalid confirmed receivable payment");
    }

    // A single committed insert makes both replay and concurrent delivery safe.
    jdbc.sql(
            """
            INSERT INTO finance.cash_movements
                (event_id, company_id, origin, direction, event_type, source_id,
                 payment_id, shift_id, account_id, method, amount, currency, occurred_at,
                 document_id, actor_id, trace_id)
            VALUES (:event, :company, 'COBRO', 'INFLOW', :type, :source,
                    :payment, :shift, :account, :method, :amount, :currency, :occurredAt,
                    :document, :actor, :trace)
            ON CONFLICT DO NOTHING
            """)
        .param("event", event.eventId())
        .param("company", event.companyId())
        .param("type", event.eventType().name())
        .param("source", event.sourceId())
        .param("payment", event.paymentId())
        .param("shift", event.shiftId())
        .param("account", event.accountId())
        .param("method", event.method().name())
        .param("amount", event.amount().setScale(2, RoundingMode.UNNECESSARY))
        .param("currency", event.currency())
        .param("occurredAt", Timestamp.from(event.occurredAt()))
        .param("document", event.documentId())
        .param("actor", event.actorId())
        .param("trace", event.traceId())
        .update();
  }
}
