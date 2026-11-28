package com.sanigest.finance.infrastructure.messaging;

import com.sanigest.events.payment.PaymentEventV1;
import java.math.RoundingMode;
import java.sql.Timestamp;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ConfirmedPaymentConsumer {
  private final ObjectMapper json;
  private final JdbcClient jdbc;

  public ConfirmedPaymentConsumer(ObjectMapper json, JdbcClient jdbc) {
    this.json = json;
    this.jdbc = jdbc;
  }

  @KafkaListener(topics = {"sales.receivable-payment.v1", "expense.payable-payment.v1"})
  public void project(ConsumerRecord<String, String> record) {
    var event = deserialize(record.value());
    boolean receivable = record.topic().equals("sales.receivable-payment.v1");
    if (!event.eventType().topic().equals(record.topic())
        || !event.companyId().toString().equals(record.key())
        || (receivable && event.shiftId() == null)) {
      throw new IllegalArgumentException("Invalid confirmed payment");
    }

    boolean reversal = event.eventType().isReversal();
    String origin = receivable ? "COBRO" : "PAGO";
    String direction = receivable != reversal ? "INFLOW" : "OUTFLOW";
    // A single committed insert makes both replay and concurrent delivery safe.
    int inserted =
        jdbc.sql(
                """
                INSERT INTO finance.cash_movements
                    (event_id, company_id, origin, direction, event_type, source_id,
                     payment_id, shift_id, account_id, method, amount, currency, occurred_at,
                     document_id, actor_id, trace_id, original_event_id)
                SELECT :event, :company, :origin, :direction, :type, :source,
                        :payment, :shift, :account, :method, :amount, :currency, :occurredAt,
                        :document, :actor, :trace, :original
                WHERE NOT :reversal OR EXISTS (
                    SELECT 1 FROM finance.cash_movements
                    WHERE company_id = :company AND event_id = :original AND source_id = :originalSource
                      AND origin = :origin AND original_event_id IS NULL AND amount = :amount
                      AND currency = :currency AND payment_id = :payment
                      AND account_id = :account AND method = :method AND occurred_at <= :occurredAt
                )
                ON CONFLICT DO NOTHING
                """)
            .param("reversal", reversal)
            .param("origin", origin)
            .param("direction", direction)
            .param("original", event.originalEventId(), java.sql.Types.OTHER)
            .param("originalSource", event.originalSourceId(), java.sql.Types.OTHER)
            .param("event", event.eventId())
            .param("company", event.companyId())
            .param("type", event.eventType().name())
            .param("source", event.sourceId())
            .param("payment", event.paymentId())
            .param("shift", event.shiftId(), java.sql.Types.OTHER)
            .param("account", event.accountId())
            .param("method", event.method().name())
            .param("amount", event.amount().setScale(2, RoundingMode.UNNECESSARY))
            .param("currency", event.currency())
            .param("occurredAt", Timestamp.from(event.occurredAt()))
            .param("document", event.documentId())
            .param("actor", event.actorId())
            .param("trace", event.traceId())
            .update();

    if (reversal
        && inserted == 0
        && !jdbc.sql(
                """
                SELECT EXISTS (SELECT 1 FROM finance.cash_movements
                  WHERE company_id = :company AND origin = :origin
                    AND original_event_id = :original AND source_id = :source)
                """)
            .param("company", event.companyId())
            .param("origin", origin)
            .param("original", event.originalEventId())
            .param("source", event.sourceId())
            .query(Boolean.class)
            .single()) {
      throw new IllegalArgumentException("Reversal does not match an original payment");
    }
  }

  private PaymentEventV1 deserialize(String payload) {
    if (payload != null) {
      try {
        var event = json.readValue(payload, PaymentEventV1.class);
        if (event != null) {
          return event;
        }
      } catch (JacksonException invalid) {
        // Do not propagate parser messages that can include input data.
      }
    }
    throw new IllegalArgumentException("Invalid payment event payload");
  }
}
