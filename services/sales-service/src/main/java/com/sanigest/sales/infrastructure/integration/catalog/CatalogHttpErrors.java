package com.sanigest.sales.infrastructure.integration.catalog;

import com.sanigest.sales.application.exception.CatalogAccessDeniedException;
import com.sanigest.sales.application.exception.CatalogResourceInvalidException;
import com.sanigest.sales.application.exception.CatalogUnavailableException;

final class CatalogHttpErrors {
  private CatalogHttpErrors() {}

  static RuntimeException forStatus(int status) {
    return switch (status) {
      case 400 ->
          new CatalogResourceInvalidException(
              CatalogResourceInvalidException.Reason.INVALID_REQUEST);
      case 401 ->
          new CatalogAccessDeniedException(
              CatalogAccessDeniedException.Reason.AUTHENTICATION_REJECTED);
      case 403 -> new CatalogAccessDeniedException(CatalogAccessDeniedException.Reason.FORBIDDEN);
      case 404 ->
          new CatalogResourceInvalidException(
              CatalogResourceInvalidException.Reason.NOT_FOUND_OR_NOT_VISIBLE);
      case 409 -> new CatalogUnavailableException(CatalogUnavailableException.Reason.CONFLICT);
      case 422 ->
          new CatalogResourceInvalidException(CatalogResourceInvalidException.Reason.NOT_ELIGIBLE);
      case 429 -> new CatalogUnavailableException(CatalogUnavailableException.Reason.RATE_LIMITED);
      default ->
          new CatalogUnavailableException(CatalogUnavailableException.Reason.DEPENDENCY_FAILURE);
    };
  }
}
