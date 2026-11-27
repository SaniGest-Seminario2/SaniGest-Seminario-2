package com.sanigest.catalog.api.error;

import com.sanigest.catalog.application.exception.InvalidSupplierQueryException;
import com.sanigest.catalog.application.exception.SupplierAccessException;
import com.sanigest.catalog.application.exception.SupplierCommercialException;
import com.sanigest.catalog.application.exception.SupplierCreationException;
import com.sanigest.catalog.domain.exception.InvalidSupplierPatch;
import com.sanigest.web.HttpProblems;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice
class SupplierErrorHandler {
  @ExceptionHandler({InvalidSupplierQueryException.class, InvalidSupplierPatch.class})
  ProblemDetail invalidRequest() {
    return HttpProblems.create(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid");
  }

  @ExceptionHandler(SupplierAccessException.class)
  ProblemDetail access(SupplierAccessException failure) {
    var code = failure.reason().name();
    return switch (failure.reason()) {
      case FORBIDDEN ->
          HttpProblems.create(HttpStatus.FORBIDDEN, code, "Supplier access is not authorized");
      case SUPPLIER_NOT_FOUND ->
          HttpProblems.create(HttpStatus.NOT_FOUND, code, "Supplier not found");
      case DUPLICATE_TAX_ID -> duplicateTaxId(code);
      case SUPPLIER_UPDATE_CONFLICT ->
          HttpProblems.create(
              HttpStatus.CONFLICT, code, "The supplier was changed by another request");
    };
  }

  @ExceptionHandler(SupplierCreationException.class)
  ProblemDetail creation(SupplierCreationException failure) {
    var code = failure.reason().name();
    return switch (failure.reason()) {
      case FORBIDDEN ->
          HttpProblems.create(HttpStatus.FORBIDDEN, code, "Supplier creation is not authorized");
      case DUPLICATE_TAX_ID -> duplicateTaxId(code);
    };
  }

  @ExceptionHandler(SupplierCommercialException.class)
  ProblemDetail commercial(SupplierCommercialException failure) {
    var reason = failure.reason();
    return switch (reason) {
      case INVALID_SUPPLIER_CATEGORY ->
          HttpProblems.create(
              HttpStatus.BAD_REQUEST, reason.name(), "Supplier category is unavailable");
      case INCOMPATIBLE_SUPPLIER_ITEM ->
          HttpProblems.create(
              HttpStatus.CONFLICT,
              reason.name(),
              "Supplier and item are not eligible for this relationship");
      case PRIMARY_SUPPLIER_CONFLICT ->
          HttpProblems.create(
              HttpStatus.CONFLICT, reason.name(), "This item already has a primary supplier");
    };
  }

  private static ProblemDetail duplicateTaxId(String code) {
    return HttpProblems.create(
        HttpStatus.CONFLICT, code, "A supplier with that tax ID already exists in the company");
  }
}
