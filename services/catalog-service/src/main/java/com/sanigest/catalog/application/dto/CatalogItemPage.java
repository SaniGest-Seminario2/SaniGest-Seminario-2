package com.sanigest.catalog.application.dto;

import com.sanigest.catalog.domain.model.CatalogItem;
import java.util.List;

public record CatalogItemPage(List<CatalogItem> content, int page, int size, long totalElements) {
  public CatalogItemPage {
    content = List.copyOf(content);
  }

  public long totalPages() {
    return Math.ceilDiv(totalElements, size);
  }
}
