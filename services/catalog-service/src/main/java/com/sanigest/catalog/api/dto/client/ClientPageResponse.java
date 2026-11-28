package com.sanigest.catalog.api.dto.client;

import com.sanigest.catalog.application.dto.client.ClientPage;
import com.sanigest.web.api.dto.PageResponse;

public final class ClientPageResponse {
  private ClientPageResponse() {}

  public static PageResponse<ClientPage.Item> from(ClientPage page) {
    return new PageResponse<>(
        page.content(), page.page(), page.size(), page.totalElements(), page.totalPages());
  }
}
