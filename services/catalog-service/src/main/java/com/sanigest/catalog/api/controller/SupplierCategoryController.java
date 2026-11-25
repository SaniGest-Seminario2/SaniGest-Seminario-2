package com.sanigest.catalog.api.controller;

import com.sanigest.catalog.application.usecase.supplier.SupplierCategories;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.model.SupplierCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  CategoryResponse create(@Valid @RequestBody CreateCategory request) {
    return CategoryResponse.from(categories.create(request.code(), request.name()));
  }

  @PutMapping("/{id}")
  CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCategory request) {
    return CategoryResponse.from(categories.update(id, request.name(), request.status()));
  }

  record CreateCategory(
      @NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}") String code,
      @NotBlank @Size(max = 100) String name) {}

  record UpdateCategory(@NotBlank @Size(max = 100) String name, @NotNull SupplierStatus status) {}

  record CategoryResponse(UUID id, String code, String name, SupplierStatus status) {
    static CategoryResponse from(SupplierCategory category) {
      return new CategoryResponse(
          category.id(), category.code(), category.name(), category.status());
    }
  }
}
