package com.sanigest.catalog.domain.exception;

public final class InvalidCatalogItem extends IllegalArgumentException {
  public InvalidCatalogItem(String message) {
    super(message);
  }
}
