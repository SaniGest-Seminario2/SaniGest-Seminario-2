package com.sanigest.catalog.application.exception.catalogitem;

public final class CatalogItemAccessException extends RuntimeException {
  public enum Reason {
    FORBIDDEN,
    CATALOG_ITEM_NOT_FOUND,
    CATALOG_ITEM_UPDATE_CONFLICT
  }

  private final Reason reason;

  public CatalogItemAccessException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
