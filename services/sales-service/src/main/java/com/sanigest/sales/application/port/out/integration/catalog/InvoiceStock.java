package com.sanigest.sales.application.port.out.integration.catalog;

import java.util.UUID;

@FunctionalInterface
public interface InvoiceStock {
  boolean isTracked(UUID item, UUID company);
}
