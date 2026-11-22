package com.sanigest.catalog.domain.exception;

public class InvalidSupplierDetails extends RuntimeException {
  public InvalidSupplierDetails(String field) {
    super("Invalid supplier " + field);
  }
}
