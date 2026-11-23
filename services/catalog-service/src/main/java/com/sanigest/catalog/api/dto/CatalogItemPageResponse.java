package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.application.dto.CatalogItemPage;
import java.util.List;

public record CatalogItemPageResponse(
    List<CatalogItemDetailResponse> content,
    int page,
    int size,
    long totalElements,
    long totalPages) {
  public static CatalogItemPageResponse from(CatalogItemPage page) {
    return new CatalogItemPageResponse(
        page.content().stream().map(CatalogItemDetailResponse::from).toList(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages());
  }
}
