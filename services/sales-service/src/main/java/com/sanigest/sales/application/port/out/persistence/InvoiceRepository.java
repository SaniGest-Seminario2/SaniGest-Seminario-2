package com.sanigest.sales.application.port.out.persistence;

import com.sanigest.sales.domain.model.InternalInvoice;
import com.sanigest.sales.domain.model.Sale;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public interface InvoiceRepository {
  record Request(UUID companyId, UUID actorId, UUID saleId, String key, int year) {}

  String issueForNewSale(
      Sale sale,
      int year,
      Function<String, InternalInvoice> create,
      Function<InternalInvoice, String> response);

  String issue(
      Request request,
      BiFunction<Sale, Supplier<String>, InternalInvoice> create,
      Function<InternalInvoice, String> response);
}
