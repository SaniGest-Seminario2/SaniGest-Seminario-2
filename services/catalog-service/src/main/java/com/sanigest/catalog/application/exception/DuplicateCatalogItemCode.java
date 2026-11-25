package com.sanigest.catalog.application.exception;

public class DuplicateCatalogItemCode extends RuntimeException {
  private final String code;

  public DuplicateCatalogItemCode(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
