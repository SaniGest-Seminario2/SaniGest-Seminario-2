package com.sanigest.catalog.api.controller;

import com.sanigest.catalog.api.dto.CreateSupplierRequest;
import com.sanigest.catalog.api.dto.SupplierResponse;
import com.sanigest.catalog.application.usecase.supplier.CreateSupplier;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SupplierController {
  private final CreateSupplier createSupplier;

  SupplierController(CreateSupplier createSupplier) {
    this.createSupplier = createSupplier;
  }

  @PostMapping("/api/v1/suppliers")
  @ResponseStatus(HttpStatus.CREATED)
  SupplierResponse create(@Valid @RequestBody CreateSupplierRequest request) {
    return SupplierResponse.from(createSupplier.execute(request.toDetails()));
  }
}
