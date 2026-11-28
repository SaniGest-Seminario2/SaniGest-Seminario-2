package com.sanigest.catalog.api.controller.client;

import com.sanigest.catalog.application.usecase.client.ClientCategories;
import com.sanigest.catalog.domain.enums.client.ClientStatus;
import com.sanigest.catalog.domain.model.client.ClientCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clients/categories")
class ClientCategoryController {
  private final ClientCategories categories;

  ClientCategoryController(ClientCategories categories) {
    this.categories = categories;
  }

  @GetMapping
  List<CategoryResponse> list() {
    return categories.list().stream().map(CategoryResponse::from).toList();
  }

  record CategoryResponse(UUID id, String code, String name, ClientStatus status) {
    static CategoryResponse from(ClientCategory category) {
      return new CategoryResponse(
          category.id(), category.code(), category.name(), category.status());
    }
  }
}
