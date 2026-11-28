package com.sanigest.events.infrastructure.persistence.outbox;

import com.sanigest.events.application.port.out.persistence.PaymentOutbox;
import com.sanigest.events.domain.model.payment.PaymentEventV1;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

public class JdbcPaymentOutbox implements PaymentOutbox {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final String table;
  private final String topic;

  public JdbcPaymentOutbox(JdbcTemplate jdbc, ObjectMapper json, String table, String topic) {
    this.jdbc = jdbc;
    this.json = json;
    this.table = table;
    this.topic = topic;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(UUID companyId, PaymentEventV1 event) {
    if (!event.companyId().equals(companyId) || !topic.equals(event.eventType().topic())) {
      throw new IllegalArgumentException(
          "Payment event does not belong to this company and service");
    }
    jdbc.update(
        """
        INSERT INTO %s (event_id, company_id, source_id, original_event_id, payload)
        VALUES (?, ?, ?, ?, ?)
        """
            .formatted(table),
        event.eventId(),
        companyId,
        event.sourceId(),
        event.originalEventId(),
        json.writeValueAsString(event));
  }
}
