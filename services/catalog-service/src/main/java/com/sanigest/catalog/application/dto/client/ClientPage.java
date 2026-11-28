package com.sanigest.catalog.application.dto.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ClientPage(List<Item> content, int page, int size, long totalElements) {
  public ClientPage {
    content = List.copyOf(content);
  }

  public long totalPages() {
    return Math.ceilDiv(totalElements, size);
  }

  public record Item(
      UUID id,
      String name,
      String taxId,
      String status,
      String phone,
      String clientType,
      String paymentTerms,
      UUID categoryId,
      String categoryName,
      BigDecimal balance) {
    public Item withBalance(BigDecimal amount) {
      return new Item(
          id,
          name,
          taxId,
          status,
          phone,
          clientType,
          paymentTerms,
          categoryId,
          categoryName,
          amount);
    }
  }
}
