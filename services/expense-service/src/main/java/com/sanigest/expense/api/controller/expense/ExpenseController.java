package com.sanigest.expense.api.controller.expense;

import com.sanigest.companyaccess.application.port.out.CompanyAccess.Scope;
import com.sanigest.companyaccess.security.authentication.AuthenticatedActor;
import com.sanigest.expense.api.dto.expense.CreateExpenseRequest;
import com.sanigest.expense.api.request.ExpenseIdempotency;
import com.sanigest.expense.api.security.ExpenseCompanyAccess;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.Document;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.RegistrationKey;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.Request;
import com.sanigest.expense.application.port.in.expense.CancelExpense;
import com.sanigest.expense.application.usecase.expense.RegisterExpense;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
class ExpenseController {
  private final RegisterExpense register;
  private final CancelExpense cancellation;
  private final ExpenseCompanyAccess companies;
  private final ObjectMapper json;

  ExpenseController(
      RegisterExpense register,
      CancelExpense cancellation,
      ExpenseCompanyAccess companies,
      ObjectMapper json) {
    this.register = register;
    this.cancellation = cancellation;
    this.companies = companies;
    this.json = json;
  }

  @PostMapping("/api/v1/expenses/{id}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Cancel an expense",
      description =
          """
          Removes the document from operational lists and totals and closes its unpaid payable.
          Detail remains available with status CANCELLED. Repeating the cancellation returns 204
          and preserves the original reason.
          """)
  @ApiResponse(responseCode = "204", description = "Expense cancelled or already cancelled")
  @ApiResponse(responseCode = "404", description = "EXPENSE_NOT_FOUND")
  @ApiResponse(responseCode = "409", description = "EXPENSE_HAS_RECEIPT or EXPENSE_HAS_PAYMENTS")
  void cancel(
      HttpServletRequest http,
      @PathVariable UUID id,
      @Valid @RequestBody CancellationRequest body) {
    var company = companies.require(http, Scope.WRITE);
    var actor = AuthenticatedActor.userId().orElseThrow(ExpenseCompanyAccess::denied);
    cancellation.cancel(company, actor, id, body.reason().strip());
  }

  record CancellationRequest(@NotBlank @Size(max = 1000) String reason) {}

  @PostMapping("/api/v1/expenses")
  @ResponseStatus(HttpStatus.CREATED)
  Document create(HttpServletRequest http, @Valid @RequestBody CreateExpenseRequest body) {
    var company = ExpenseCompanyAccess.select(http);
    var actor = AuthenticatedActor.userId().orElseThrow(ExpenseCompanyAccess::denied);
    companies.require(company, Scope.WRITE);
    Request request = body.toCommand();
    return register.register(
        new RegistrationKey(company, actor, ExpenseIdempotency.key(http)), hash(request), request);
  }

  private String hash(Request request) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
