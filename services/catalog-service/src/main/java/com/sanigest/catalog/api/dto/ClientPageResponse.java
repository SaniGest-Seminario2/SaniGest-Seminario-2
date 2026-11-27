package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.application.dto.ClientPage;
import com.sanigest.web.PageResponse;

public final class ClientPageResponse {
  private ClientPageResponse() {}

  public static PageResponse<ClientPage.Item> from(ClientPage page) {
    return new PageResponse<>(
        page.content(), page.page(), page.size(), page.totalElements(), page.totalPages());
  }
}
