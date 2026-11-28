package com.sanigest.sales.infrastructure.persistence.jpa;

import com.sanigest.security.RequestCorrelation;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class SalesAudit {
  private final JdbcTemplate jdbc;

  SalesAudit(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void append(UUID company, UUID actor, Operation operation, UUID resource, String state) {
    jdbc.update(
        """
        INSERT INTO sales.audit_log
            (id, company_id, actor_id, operation, resource_id, state, correlation_id)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        company,
        actor,
        operation.name(),
        resource,
        state,
        RequestCorrelation.current());
  }

  enum Operation {
    SALE_REGISTERED,
    SALE_CANCELLED,
    INVOICE_ISSUED,
    INVOICE_CANCELLED,
    INVOICE_PAYMENT_REGISTERED
  }
}
