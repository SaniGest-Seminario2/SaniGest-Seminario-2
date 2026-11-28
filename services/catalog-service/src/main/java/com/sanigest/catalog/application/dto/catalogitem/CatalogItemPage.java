package com.sanigest.catalog.application.dto.catalogitem;

import com.sanigest.catalog.domain.model.catalogitem.CatalogItem;
import java.util.List;

public record CatalogItemPage(List<CatalogItem> content, int page, int size, long totalElements) {
  public CatalogItemPage {
    content = List.copyOf(content);
  }

  public long totalPages() {
    return Math.ceilDiv(totalElements, size);
  }
}
