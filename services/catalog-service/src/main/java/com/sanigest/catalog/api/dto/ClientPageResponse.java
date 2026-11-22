package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.application.ClientPage;
import java.util.List;

public record ClientPageResponse(
    List<ClientPage.Item> content, int page, int size, long totalElements, long totalPages) {
  public static ClientPageResponse from(ClientPage page) {
    return new ClientPageResponse(
        page.content(), page.page(), page.size(), page.totalElements(), page.totalPages());
  }
}
