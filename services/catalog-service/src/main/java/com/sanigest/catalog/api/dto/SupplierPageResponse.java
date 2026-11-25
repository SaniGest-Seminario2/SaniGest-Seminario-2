package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.application.dto.SupplierPage;
import com.sanigest.catalog.domain.enums.SupplierPaymentTerms;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.enums.SupplierSupplyType;
import java.util.List;
import java.util.UUID;

public record SupplierPageResponse(
    List<Item> content, int page, int size, long totalElements, long totalPages) {
  public static SupplierPageResponse from(SupplierPage page) {
    var content =
        page.content().stream()
            .map(item ->
                new Item(
                    item.id(),
                    item.name(),
                    item.taxId(),
                    item.status(),
                    item.contact(),
                    item.phone(),
                    item.supplyType(),
                    item.paymentTerms(),
                    item.categoryId()))
            .toList();
    return new SupplierPageResponse(
        content, page.page(), page.size(), page.totalElements(), page.totalPages());
  }

  public record Item(
      UUID id,
      String name,
      String taxId,
      SupplierStatus status,
      String contact,
      String phone,
      SupplierSupplyType supplyType,
      SupplierPaymentTerms paymentTerms,
      UUID categoryId) {
    public Item(UUID id, String name, String taxId, SupplierStatus status) {
      this(id, name, taxId, status, null, null, null, null, null);
    }
  }
}
