package com.sanigest.sales.api.controller;

import com.sanigest.sales.api.dto.ReceivableDetailResponse;
import com.sanigest.sales.application.usecase.receivable.GetReceivable;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReceivableController {
  private final GetReceivable getReceivable;

  ReceivableController(GetReceivable getReceivable) {
    this.getReceivable = getReceivable;
  }

  @GetMapping("/api/v1/receivables/{id}")
  @PreAuthorize("isAuthenticated()")
  public ReceivableDetailResponse detail(@PathVariable UUID id) {
    return ReceivableDetailResponse.from(getReceivable.execute(id));
  }
}
