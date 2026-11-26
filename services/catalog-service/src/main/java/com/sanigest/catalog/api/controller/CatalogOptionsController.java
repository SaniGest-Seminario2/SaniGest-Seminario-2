package com.sanigest.catalog.api.controller;

import com.sanigest.catalog.api.dto.CatalogOptions;
import com.sanigest.catalog.api.dto.NamedOption;
import com.sanigest.catalog.application.dto.ClientSearchCriteria;
import com.sanigest.catalog.application.dto.SupplierSearchCriteria;
import com.sanigest.catalog.application.usecase.catalogitem.ListCatalogItems;
import com.sanigest.catalog.application.usecase.catalogitem.ListCatalogItems.CommercialFilters;
import com.sanigest.catalog.application.usecase.client.ListClients;
import com.sanigest.catalog.application.usecase.supplier.ListSuppliers;
import com.sanigest.catalog.domain.enums.ClientStatus;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.enums.SupplierSupplyType;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CatalogOptionsController {
  private static final CatalogOptions OPTIONS = CatalogOptions.defaults();
  private static final int LIMIT = 20;
  private final ListClients clients;
  private final ListSuppliers suppliers;
  private final ListCatalogItems items;

  CatalogOptionsController(ListClients clients, ListSuppliers suppliers, ListCatalogItems items) {
    this.clients = clients;
    this.suppliers = suppliers;
    this.items = items;
  }

  @GetMapping("/api/v1/catalog/options")
  CatalogOptions options() {
    items.authorizeRead();
    return OPTIONS;
  }

  @GetMapping("/api/v1/clients/options")
  List<NamedOption> clients(@RequestParam(required = false) String search) {
    var criteria =
        new ClientSearchCriteria(search, ClientStatus.ACTIVE.name(), null, null, null, false);
    return clients.execute(criteria, 0, LIMIT).content().stream()
        .map(client -> new NamedOption(client.id(), client.name()))
        .toList();
  }

  @GetMapping("/api/v1/suppliers/options")
  List<NamedOption> suppliers(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) SupplierSupplyType supplyType) {
    var criteria =
        new SupplierSearchCriteria(
            search == null ? null : search.strip(),
            SupplierStatus.ACTIVE,
            supplyType,
            null,
            null,
            false);
    return suppliers.execute(criteria, 0, LIMIT).content().stream()
        .map(supplier -> new NamedOption(supplier.id(), supplier.name()))
        .toList();
  }

  @GetMapping("/api/v1/catalog-items/options")
  List<NamedOption> items(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Operation operation) {
    var filters =
        new CommercialFilters(
            null,
            operation == Operation.SALE ? "true" : null,
            operation == Operation.PURCHASE ? "true" : null);
    return items.execute(type, search, "true", filters, 0, LIMIT).content().stream()
        .map(item -> new NamedOption(item.id(), item.name()))
        .toList();
  }

  enum Operation {
    SALE,
    PURCHASE
  }
}
