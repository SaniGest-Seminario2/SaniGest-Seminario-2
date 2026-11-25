package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.application.dto.CatalogItemCommercialData;
import com.sanigest.catalog.application.dto.CatalogItemPage;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CatalogItemPageResponse(
    List<CatalogItemDetailResponse> content,
    int page,
    int size,
    long totalElements,
    long totalPages) {
  public static CatalogItemPageResponse from(CatalogItemPage page) {
    return from(page, Map.of());
  }

  public static CatalogItemPageResponse from(
      CatalogItemPage page, Map<UUID, CatalogItemCommercialData> details) {
    return new CatalogItemPageResponse(
        page.content().stream()
            .map(item ->
                CatalogItemDetailResponse.from(
                    item, details.getOrDefault(item.id(), CatalogItemCommercialData.EMPTY)))
            .toList(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages());
  }
}
