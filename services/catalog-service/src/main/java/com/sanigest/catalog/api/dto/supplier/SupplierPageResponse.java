package com.sanigest.catalog.api.dto.supplier;

import com.sanigest.catalog.application.usecase.supplier.ListSuppliers.WithBalances;
import com.sanigest.catalog.domain.enums.supplier.SupplierPaymentTerms;
import com.sanigest.catalog.domain.enums.supplier.SupplierStatus;
import com.sanigest.catalog.domain.enums.supplier.SupplierSupplyType;
import com.sanigest.web.api.dto.PageResponse;
import java.math.BigDecimal;
import java.util.UUID;

public final class SupplierPageResponse {
  private SupplierPageResponse() {}

  public static PageResponse<SupplierPageResponse.Item> from(WithBalances result) {
    var page = result.page();
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
                    item.categoryId(),
                    result.balances().getOrDefault(item.id(), new BigDecimal("0.00"))))
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
      UUID categoryId,
      BigDecimal outstandingAmount) {}
}
