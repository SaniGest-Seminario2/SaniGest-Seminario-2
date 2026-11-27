package com.sanigest.sales.api.controller;

import com.sanigest.sales.application.usecase.invoice.ListActiveShiftPayments;
import com.sanigest.sales.domain.model.InvoicePayment;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("isAuthenticated()")
public class ActiveShiftPaymentController {
  private final ListActiveShiftPayments history;

  ActiveShiftPaymentController(ListActiveShiftPayments history) {
    this.history = history;
  }

  @GetMapping("/api/v1/cash-sessions/active/payments")
  public List<InvoicePayment.HistoryLine> list() {
    return history.execute();
  }
}
