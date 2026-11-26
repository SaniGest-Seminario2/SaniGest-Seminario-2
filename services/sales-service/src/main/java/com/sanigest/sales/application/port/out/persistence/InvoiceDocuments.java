package com.sanigest.sales.application.port.out.persistence;

import com.sanigest.sales.application.dto.InvoicePage;
import com.sanigest.sales.domain.model.SaleCancellation;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceDocuments {
  record Search(String pattern, String status, boolean collectible, int offset, Integer limit) {}

  InvoicePage list(UUID company, Search search);

  Optional<String> find(UUID company, UUID invoice);

  Optional<String> cancel(UUID company, UUID invoice, SaleCancellation cancellation);
}
