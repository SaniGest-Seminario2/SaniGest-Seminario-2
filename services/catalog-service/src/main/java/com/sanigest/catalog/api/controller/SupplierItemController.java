package com.sanigest.catalog.api.controller;

import static com.sanigest.catalog.application.dto.SupplierItemQuery.Direction.ITEM_SUPPLIERS;
import static com.sanigest.catalog.application.dto.SupplierItemQuery.Direction.SUPPLIED_ITEMS;

import com.sanigest.catalog.api.dto.SupplierItemPageResponse;
import com.sanigest.catalog.api.dto.SupplierItemsRequest;
import com.sanigest.catalog.application.usecase.supplier.ListSupplierLinks;
import com.sanigest.catalog.application.usecase.supplier.ListSuppliers;
import com.sanigest.catalog.application.usecase.supplier.ReplaceSupplierItems;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
class SupplierItemController {
  private final ListSupplierLinks list;
  private final ReplaceSupplierItems replace;

  SupplierItemController(ListSupplierLinks list, ReplaceSupplierItems replace) {
    this.list = list;
    this.replace = replace;
  }

  @GetMapping("/api/v1/suppliers/{id}/items")
  SupplierItemPageResponse<SupplierItemPageResponse.SuppliedItem> items(
      @PathVariable UUID id,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) SupplierStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = ListSuppliers.DEFAULT_SIZE) int size) {
    return SupplierItemPageResponse.items(
        list.execute(id, SUPPLIED_ITEMS, search, status, page, size));
  }

  @GetMapping("/api/v1/catalog-items/{id}/suppliers")
  SupplierItemPageResponse<SupplierItemPageResponse.ItemSupplier> suppliers(
      @PathVariable UUID id,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) SupplierStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = ListSuppliers.DEFAULT_SIZE) int size) {
    return SupplierItemPageResponse.suppliers(
        list.execute(id, ITEM_SUPPLIERS, search, status, page, size));
  }

  @PutMapping("/api/v1/suppliers/{id}/items")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void replace(@PathVariable UUID id, @RequestBody JsonNode body) {
    var request = SupplierItemsRequest.parse(body);
    replace.execute(id, request.items(), request.replacePrimary());
  }
}
