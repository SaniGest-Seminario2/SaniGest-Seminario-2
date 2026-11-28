package com.sanigest.finance.application.usecase;

import com.sanigest.finance.application.port.out.integration.FinancialSnapshotSources;
import com.sanigest.finance.application.port.out.persistence.FinancialProjectionStore;
import java.util.UUID;

public class FinancialProjections {
  private final FinancialProjectionStore store;
  private final FinancialSnapshotSources sources;

  public FinancialProjections(FinancialProjectionStore store, FinancialSnapshotSources sources) {
    this.store = store;
    this.sources = sources;
  }

  public String read(UUID company, String token) {
    sources.authorize(company, token);
    return store.view(company);
  }

  public String rebuild(UUID company, String token) {
    store.save(company, sources.fetch(company, token));
    return store.view(company);
  }
}
