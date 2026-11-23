package com.sanigest.catalog.api.controller;

import com.sanigest.catalog.api.dto.CreateSupplierRequest;
import com.sanigest.catalog.api.dto.SupplierDetailResponse;
import com.sanigest.catalog.api.dto.SupplierPageResponse;
import com.sanigest.catalog.api.dto.SupplierPatchRequest;
import com.sanigest.catalog.api.dto.SupplierResponse;
import com.sanigest.catalog.application.exception.InvalidSupplierQueryException;
import com.sanigest.catalog.application.usecase.supplier.ChangeSupplierStatus;
import com.sanigest.catalog.application.usecase.supplier.CreateSupplier;
import com.sanigest.catalog.application.usecase.supplier.GetSupplier;
import com.sanigest.catalog.application.usecase.supplier.ListSuppliers;
import com.sanigest.catalog.application.usecase.supplier.UpdateSupplier;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
class SupplierController {
  private final CreateSupplier createSupplier;
  private final ListSuppliers listSuppliers;
  private final GetSupplier getSupplier;
  private final UpdateSupplier updateSupplier;
  private final ChangeSupplierStatus changeSupplierStatus;

  SupplierController(
      CreateSupplier createSupplier,
      ListSuppliers listSuppliers,
      GetSupplier getSupplier,
      UpdateSupplier updateSupplier,
      ChangeSupplierStatus changeSupplierStatus) {
    this.createSupplier = createSupplier;
    this.listSuppliers = listSuppliers;
    this.getSupplier = getSupplier;
    this.updateSupplier = updateSupplier;
    this.changeSupplierStatus = changeSupplierStatus;
  }

  @GetMapping("/api/v1/suppliers/{id}")
  SupplierDetailResponse detail(@PathVariable String id) {
    return SupplierDetailResponse.from(getSupplier.execute(supplierId(id)));
  }

  @PatchMapping("/api/v1/suppliers/{id}")
  SupplierDetailResponse update(@PathVariable String id, @RequestBody JsonNode body) {
    return SupplierDetailResponse.from(
        updateSupplier.execute(supplierId(id), SupplierPatchRequest.details(body)));
  }

  @PatchMapping("/api/v1/suppliers/{id}/status")
  SupplierDetailResponse status(@PathVariable String id, @RequestBody JsonNode body) {
    return SupplierDetailResponse.from(
        changeSupplierStatus.execute(supplierId(id), SupplierPatchRequest.status(body)));
  }

  private static UUID supplierId(String id) {
    try {
      var uuid = UUID.fromString(id);
      if (uuid.toString().equalsIgnoreCase(id)) {
        return uuid;
      }
    } catch (IllegalArgumentException invalid) {
      throw new InvalidSupplierQueryException();
    }
    throw new InvalidSupplierQueryException();
  }

  @GetMapping("/api/v1/suppliers")
  SupplierPageResponse list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = ListSuppliers.DEFAULT_SIZE) int size) {
    return SupplierPageResponse.from(listSuppliers.execute(search, status, page, size));
  }

  @PostMapping("/api/v1/suppliers")
  @ResponseStatus(HttpStatus.CREATED)
  SupplierResponse create(@Valid @RequestBody CreateSupplierRequest request) {
    return SupplierResponse.from(createSupplier.execute(request.toDetails()));
  }
}
