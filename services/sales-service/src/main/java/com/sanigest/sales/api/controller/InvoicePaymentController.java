package com.sanigest.sales.api.controller;

import com.sanigest.sales.application.usecase.invoice.RegisterInvoicePayment;
import com.sanigest.sales.domain.model.InvoicePayment;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("isAuthenticated()")
public class InvoicePaymentController {
  private final RegisterInvoicePayment payments;

  InvoicePaymentController(RegisterInvoicePayment payments) {
    this.payments = payments;
  }

  @PostMapping("/api/v1/invoices/{id}/payments")
  @ResponseStatus(HttpStatus.CREATED)
  public InvoicePayment.Result register(
      @PathVariable UUID id,
      @RequestHeader HttpHeaders headers,
      @RequestBody InvoicePayment.Request request) {
    var keys = headers.get("Idempotency-Key");
    if (keys == null || keys.size() != 1 || !keys.getFirst().matches("[A-Za-z0-9._~-]{1,128}")) {
      throw new InvalidIdempotencyKey();
    }
    return payments.execute(id, keys.getFirst(), request);
  }
}
