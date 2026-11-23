package com.sanigest.sales.api.controller;

import com.sanigest.sales.api.dto.CreateSaleRequest;
import com.sanigest.sales.api.dto.SaleResponse;
import com.sanigest.sales.application.usecase.sale.CreateSale;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SaleController {
  private final CreateSale createSale;

  SaleController(CreateSale createSale) {
    this.createSale = createSale;
  }

  @PostMapping("/api/v1/sales")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  public SaleResponse create(@Valid @RequestBody CreateSaleRequest request) {
    return SaleResponse.from(createSale.execute(request.toCommand()));
  }
}
