package com.sanigest.catalog.api.dto.catalogitem;

import com.sanigest.catalog.application.dto.catalogitem.CatalogItemCommercialData;
import com.sanigest.catalog.application.dto.catalogitem.CatalogItemPage;
import com.sanigest.web.api.dto.PageResponse;
import java.util.Map;
import java.util.UUID;

public final class CatalogItemPageResponse {
  private CatalogItemPageResponse() {}

  public static PageResponse<CatalogItemDetailResponse> from(CatalogItemPage page) {
    return from(page, Map.of());
  }

  public static PageResponse<CatalogItemDetailResponse> from(
      CatalogItemPage page, Map<UUID, CatalogItemCommercialData> details) {
    return new PageResponse<>(
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
