package com.sanigest.sales.infrastructure.persistence.jpa.sale;

import com.sanigest.companyaccess.security.authentication.AuthenticatedActor;
import com.sanigest.sales.application.exception.invoice.InvoiceStockUnavailable;
import com.sanigest.sales.domain.model.sale.Sale;
import com.sanigest.sales.domain.model.sale.SaleCancellation;
import com.sanigest.sales.domain.model.sale.SaleLine;
import com.sanigest.sales.infrastructure.integration.inventory.SaleStockClient;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

@Component
class SaleStockIntents {
  private static final Logger LOG = LoggerFactory.getLogger(SaleStockIntents.class);
  private final JdbcTemplate jdbc;
  private final SaleStockClient inventory;
  private final TransactionTemplate independent;

  SaleStockIntents(
      JdbcTemplate jdbc, SaleStockClient inventory, PlatformTransactionManager manager) {
    this.jdbc = jdbc;
    this.inventory = inventory;
    independent = new TransactionTemplate(manager);
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  void issue(Sale sale) {
    if (!inventory.enabled()) {
      return;
    }
    var actor = AuthenticatedActor.userId().orElseThrow(InvoiceStockUnavailable::new);
    var quantities =
        sale.lines().stream()
            .collect(
                Collectors.toMap(SaleLine::catalogItemId, SaleLine::quantity, BigDecimal::add));
    // A rollback or process crash leaves the independently committed compensation intent due.
    independent.executeWithoutResult(status ->
        jdbc.update(
            """
            INSERT INTO sales.sale_stock_intents (company_id, sale_id, actor_id, revert_after)
            VALUES (?, ?, ?, clock_timestamp() + interval '1 minute')
            """,
            sale.companyId(),
            sale.id(),
            actor));
    int locked =
        jdbc.update(
            """
            UPDATE sales.sale_stock_intents SET revert_after = NULL
            WHERE company_id = ? AND sale_id = ? AND revert_after > clock_timestamp()
            """,
            sale.companyId(),
            sale.id());
    if (locked != 1) {
      throw new InvoiceStockUnavailable();
    }
    inventory.issue(sale.companyId(), sale.id(), actor, quantities);
  }

  void cancel(Sale sale, SaleCancellation cancellation) {
    jdbc.update(
        """
        UPDATE sales.sale_stock_intents SET revert_after = clock_timestamp(), actor_id = ?
        WHERE company_id = ? AND sale_id = ?
        """,
        cancellation.cancelledBy(),
        sale.companyId(),
        sale.id());
  }

  @Scheduled(fixedDelay = 1000)
  void compensate() {
    if (!inventory.enabled()) {
      return;
    }
    for (int i = 0; i < 20; i++) {
      if (!Boolean.TRUE.equals(independent.execute(status -> compensateOne()))) {
        return;
      }
    }
  }

  private boolean compensateOne() {
    var pending =
        jdbc.query(
            """
            SELECT company_id, sale_id, actor_id FROM sales.sale_stock_intents
            WHERE revert_after <= clock_timestamp()
            ORDER BY revert_after LIMIT 1 FOR UPDATE SKIP LOCKED
            """,
            (row, index) ->
                new Pending(
                    row.getObject(1, UUID.class),
                    row.getObject(2, UUID.class),
                    row.getObject(3, UUID.class)));
    if (pending.isEmpty()) {
      return false;
    }
    var intent = pending.getFirst();
    try {
      inventory.reverse(intent.company(), intent.sale(), intent.actor());
    } catch (RestClientException failure) {
      LOG.warn("Sale stock compensation will retry for sale {}", intent.sale());
      jdbc.update(
          """
          UPDATE sales.sale_stock_intents SET revert_after = clock_timestamp() + interval '15 seconds'
          WHERE company_id = ? AND sale_id = ?
          """,
          intent.company(),
          intent.sale());
      return true;
    }
    jdbc.update(
        "DELETE FROM sales.sale_stock_intents WHERE company_id = ? AND sale_id = ?",
        intent.company(),
        intent.sale());
    return true;
  }

  private record Pending(UUID company, UUID sale, UUID actor) {}
}
