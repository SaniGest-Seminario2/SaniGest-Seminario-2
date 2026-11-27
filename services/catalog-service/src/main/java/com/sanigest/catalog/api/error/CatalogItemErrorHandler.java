package com.sanigest.catalog.api.error;

import static com.sanigest.web.HttpProblems.create;

import com.sanigest.catalog.application.exception.CatalogItemAccessException;
import com.sanigest.catalog.application.exception.CatalogItemCreationForbidden;
import com.sanigest.catalog.application.exception.DuplicateCatalogItemCode;
import com.sanigest.catalog.application.exception.InvalidCatalogItemQuery;
import com.sanigest.catalog.application.exception.InventoryLookupFailed;
import com.sanigest.catalog.domain.exception.InvalidCatalogItem;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice
class CatalogItemErrorHandler {
  @ExceptionHandler({InvalidCatalogItem.class, InvalidCatalogItemQuery.class})
  ProblemDetail invalidItem() {
    return create(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The catalog item is invalid");
  }

  @ExceptionHandler(InventoryLookupFailed.class)
  ProblemDetail inventory(InventoryLookupFailed failure) {
    return switch (failure.reason()) {
      case FORBIDDEN ->
          create(
              HttpStatus.FORBIDDEN,
              "INVENTORY_ACCESS_DENIED",
              "Inventory access is not authorized");
      case WAREHOUSE_NOT_FOUND ->
          create(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", "Warehouse not found");
      case UNAVAILABLE ->
          create(
              HttpStatus.SERVICE_UNAVAILABLE, "INVENTORY_UNAVAILABLE", "Inventory is unavailable");
    };
  }

  @ExceptionHandler(DuplicateCatalogItemCode.class)
  ProblemDetail duplicate(DuplicateCatalogItemCode failure) {
    return create(HttpStatus.CONFLICT, failure.code(), "The commercial code is already in use");
  }

  @ExceptionHandler(CatalogItemCreationForbidden.class)
  ProblemDetail forbidden() {
    return create(HttpStatus.FORBIDDEN, "FORBIDDEN", "Catalog item creation is not authorized");
  }

  @ExceptionHandler(CatalogItemAccessException.class)
  ProblemDetail access(CatalogItemAccessException failure) {
    var code = failure.reason().name();
    return switch (failure.reason()) {
      case FORBIDDEN -> create(HttpStatus.FORBIDDEN, code, "Catalog item access is not authorized");
      case CATALOG_ITEM_NOT_FOUND -> create(HttpStatus.NOT_FOUND, code, "Catalog item not found");
      case CATALOG_ITEM_UPDATE_CONFLICT ->
          create(HttpStatus.CONFLICT, code, "The catalog item was changed by another request");
    };
  }
}
