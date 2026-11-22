package com.sanigest.catalog.application.dto;

import java.util.List;
import java.util.UUID;

public record ClientPage(List<Item> content, int page, int size, long totalElements) {
  public ClientPage {
    content = List.copyOf(content);
  }

  public long totalPages() {
    return Math.ceilDiv(totalElements, size);
  }

  public record Item(UUID id, String name, String taxId, String status) {}
}
