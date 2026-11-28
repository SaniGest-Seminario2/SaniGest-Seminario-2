package com.sanigest.catalog.api.controller.supplier;

import com.sanigest.catalog.application.usecase.supplier.SupplierCategories;
import com.sanigest.catalog.domain.enums.supplier.SupplierStatus;
import com.sanigest.catalog.domain.model.supplier.SupplierCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/suppliers/categories")
class SupplierCategoryController {
  private final SupplierCategories categories;

  SupplierCategoryController(SupplierCategories categories) {
    this.categories = categories;
  }

  @GetMapping
  List<CategoryResponse> list() {
    return categories.list().stream().map(CategoryResponse::from).toList();
  }

  record CategoryResponse(UUID id, String code, String name, SupplierStatus status) {
    static CategoryResponse from(SupplierCategory category) {
      return new CategoryResponse(
          category.id(), category.code(), category.name(), category.status());
    }
  }
}
