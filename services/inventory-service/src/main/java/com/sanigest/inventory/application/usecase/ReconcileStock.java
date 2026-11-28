package com.sanigest.inventory.application.usecase;

import com.sanigest.inventory.application.port.out.persistence.StockLedger;
import com.sanigest.inventory.domain.model.StockKey;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReconcileStock {
  private static final Logger LOG = LoggerFactory.getLogger(ReconcileStock.class);
  private final StockLedger ledger;

  public ReconcileStock(StockLedger ledger) {
    this.ledger = ledger;
  }

  public Optional<StockLedger.Reconciliation> check(StockKey key) {
    return ledger
        .reconcile(key)
        .map(result -> {
          if (result.difference().signum() != 0) {
            LOG.warn(
                "Physical balance divergence company={} warehouse={} item={} difference={}",
                key.companyId(),
                key.warehouseId(),
                key.catalogItemId(),
                result.difference());
          }
          return result;
        });
  }
}
