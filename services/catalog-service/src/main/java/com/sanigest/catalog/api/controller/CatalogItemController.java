package com.sanigest.catalog.api.controller;

import com.sanigest.catalog.api.dto.CatalogItemDetailResponse;
import com.sanigest.catalog.api.dto.CatalogItemPageResponse;
import com.sanigest.catalog.api.dto.CatalogItemPatchRequest;
import com.sanigest.catalog.api.dto.CatalogItemResponse;
import com.sanigest.catalog.api.dto.CatalogItemStatusRequest;
import com.sanigest.catalog.api.dto.CreateCatalogItemRequest;
import com.sanigest.catalog.application.exception.InvalidCatalogItemQuery;
import com.sanigest.catalog.application.usecase.catalogitem.ChangeCatalogItemStatus;
import com.sanigest.catalog.application.usecase.catalogitem.CreateCatalogItem;
import com.sanigest.catalog.application.usecase.catalogitem.GetCatalogItem;
import com.sanigest.catalog.application.usecase.catalogitem.ListCatalogItems;
import com.sanigest.catalog.application.usecase.catalogitem.UpdateCatalogItem;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog-items")
class CatalogItemController {
  private final CreateCatalogItem createCatalogItem;

  private final ListCatalogItems listCatalogItems;
  private final GetCatalogItem getCatalogItem;
  private final UpdateCatalogItem updateCatalogItem;
  private final ChangeCatalogItemStatus changeCatalogItemStatus;

  CatalogItemController(
      CreateCatalogItem createCatalogItem,
      ListCatalogItems listCatalogItems,
      GetCatalogItem getCatalogItem,
      UpdateCatalogItem updateCatalogItem,
      ChangeCatalogItemStatus changeCatalogItemStatus) {
    this.createCatalogItem = createCatalogItem;
    this.listCatalogItems = listCatalogItems;
    this.getCatalogItem = getCatalogItem;
    this.updateCatalogItem = updateCatalogItem;
    this.changeCatalogItemStatus = changeCatalogItemStatus;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  CatalogItemResponse create(@Valid @RequestBody CreateCatalogItemRequest request) {
    return CatalogItemResponse.from(
        createCatalogItem.execute(request.name(), request.type(), request.price()));
  }

  @GetMapping
  CatalogItemPageResponse list(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = ListCatalogItems.DEFAULT_SIZE) int size) {
    return CatalogItemPageResponse.from(listCatalogItems.execute(type, search, active, page, size));
  }

  @GetMapping("/{id}")
  ResponseEntity<CatalogItemDetailResponse> detail(
      @PathVariable String id, @RequestParam(required = false) UUID expectedCompanyId) {
    var detail =
        CatalogItemDetailResponse.from(getCatalogItem.execute(parseId(id), expectedCompanyId));
    var response = ResponseEntity.ok();
    if (expectedCompanyId != null) {
      response.header("X-Catalog-Company-Id", expectedCompanyId.toString());
    }
    return response.body(detail);
  }

  @PatchMapping("/{id}")
  CatalogItemDetailResponse update(
      @PathVariable String id, @RequestBody CatalogItemPatchRequest request) {
    return CatalogItemDetailResponse.from(updateCatalogItem.execute(parseId(id), request.patch()));
  }

  @PatchMapping("/{id}/status")
  CatalogItemDetailResponse changeStatus(
      @PathVariable String id, @RequestBody CatalogItemStatusRequest request) {
    return CatalogItemDetailResponse.from(
        changeCatalogItemStatus.execute(parseId(id), request.active()));
  }

  private static UUID parseId(String rawId) {
    UUID id;
    try {
      id = UUID.fromString(rawId);
    } catch (IllegalArgumentException invalid) {
      throw new InvalidCatalogItemQuery();
    }
    if (!id.toString().equalsIgnoreCase(rawId)) {
      throw new InvalidCatalogItemQuery();
    }
    return id;
  }
}
