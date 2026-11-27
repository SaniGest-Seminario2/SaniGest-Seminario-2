package com.sanigest.expense.api;

import com.sanigest.companyaccess.AuthenticatedActor;
import com.sanigest.companyaccess.CompanyAccess.Scope;
import com.sanigest.expense.api.dto.PayablePaymentRequest;
import com.sanigest.expense.application.usecase.RegisterPayablePayment;
import com.sanigest.expense.domain.model.PayablePayment.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PayablePaymentController {
  private final ExpenseCompanyAccess companies;
  private final RegisterPayablePayment payments;

  PayablePaymentController(ExpenseCompanyAccess companies, RegisterPayablePayment payments) {
    this.companies = companies;
    this.payments = payments;
  }

  @PostMapping("/api/v1/payables/{id}/payments")
  @ResponseStatus(HttpStatus.CREATED)
  Result register(
      HttpServletRequest http,
      @PathVariable UUID id,
      @Valid @RequestBody PayablePaymentRequest request) {
    var company = companies.require(http, Scope.WRITE);
    var actor = AuthenticatedActor.userId().orElseThrow(ExpenseCompanyAccess::denied);
    return payments.register(
        company, actor, id, ExpenseController.idempotencyKey(http), request.details());
  }
}
