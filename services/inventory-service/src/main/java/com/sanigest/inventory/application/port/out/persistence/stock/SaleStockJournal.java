package com.sanigest.inventory.application.port.out.persistence.stock;

import com.sanigest.inventory.domain.model.stock.StockKey;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SaleStockJournal {
  boolean claim(UUID company, UUID sale, String hash, boolean reverse);

  void lockWarehouses(UUID company);

  List<StockKey> tracked(UUID company, Collection<UUID> items);

  List<Issue> issues(UUID company, UUID sale);

  record Issue(StockKey stock, BigDecimal quantity) {}
}
