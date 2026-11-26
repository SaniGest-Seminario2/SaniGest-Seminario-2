package com.sanigest.sales.application.port.out.integration;

import com.sanigest.sales.domain.model.CatalogCustomerSnapshot;
import com.sanigest.sales.domain.model.CatalogItemSnapshot;
import java.util.UUID;

/** Loads sale-eligible snapshots within the authenticated actor's current Catalog membership. */
public interface CatalogClient {
  CatalogCustomerSnapshot getCustomer(UUID customerId, UUID expectedCompanyId);

  CatalogItemSnapshot getItem(UUID catalogItemId, UUID expectedCompanyId);

  default CatalogItemSnapshot getInvoiceItem(UUID itemId, UUID companyId) {
    return getItem(itemId, companyId);
  }
}
