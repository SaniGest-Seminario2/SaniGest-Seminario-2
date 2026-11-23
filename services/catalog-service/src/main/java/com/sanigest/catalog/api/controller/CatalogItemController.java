package com.sanigest.catalog.api.controller;

import com.sanigest.catalog.api.dto.CatalogItemResponse;
import com.sanigest.catalog.api.dto.CreateCatalogItemRequest;
import com.sanigest.catalog.application.usecase.catalogitem.CreateCatalogItem;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CatalogItemController {
  private final CreateCatalogItem createCatalogItem;

  CatalogItemController(CreateCatalogItem createCatalogItem) {
    this.createCatalogItem = createCatalogItem;
  }

  @PostMapping("/api/v1/catalog-items")
  @ResponseStatus(HttpStatus.CREATED)
  CatalogItemResponse create(@Valid @RequestBody CreateCatalogItemRequest request) {
    return CatalogItemResponse.from(
        createCatalogItem.execute(request.name(), request.type(), request.price()));
  }
}
