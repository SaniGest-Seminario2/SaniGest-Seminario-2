package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.application.dto.SupplierPage;
import com.sanigest.catalog.domain.enums.SupplierPaymentTerms;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.enums.SupplierSupplyType;
import com.sanigest.web.PageResponse;
import java.util.UUID;

public final class SupplierPageResponse {
  private SupplierPageResponse() {}

  public static PageResponse<SupplierPageResponse.Item> from(SupplierPage page) {
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
                    SupplierSupplyCoverage.from(item.supplyType()),
                    item.supplyType(),
                    item.paymentTerms(),
                    item.categoryId()))
            .toList();
    return new PageResponse<>(
        content, page.page(), page.size(), page.totalElements(), page.totalPages());
  }

  public record Item(
      UUID id,
      String name,
      String taxId,
      SupplierStatus status,
      String contact,
      String phone,
      SupplierSupplyCoverage supplyType,
      SupplierSupplyType supplyClassification,
      SupplierPaymentTerms paymentTerms,
      UUID categoryId) {
    public Item(UUID id, String name, String taxId, SupplierStatus status) {
      this(id, name, taxId, status, null, null, null, null, null, null);
    }
  }
}
