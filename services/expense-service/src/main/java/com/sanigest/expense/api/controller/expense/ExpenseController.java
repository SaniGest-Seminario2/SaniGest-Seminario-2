package com.sanigest.expense.api.controller.expense;

import com.sanigest.companyaccess.application.port.out.CompanyAccess.Scope;
import com.sanigest.companyaccess.security.authentication.AuthenticatedActor;
import com.sanigest.expense.api.dto.expense.CreateExpenseRequest;
import com.sanigest.expense.api.request.ExpenseIdempotency;
import com.sanigest.expense.api.security.ExpenseCompanyAccess;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.Document;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.RegistrationKey;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.Request;
import com.sanigest.expense.application.usecase.expense.RegisterExpense;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
class ExpenseController {
  private final RegisterExpense register;
  private final ExpenseCompanyAccess companies;
  private final ObjectMapper json;

  ExpenseController(RegisterExpense register, ExpenseCompanyAccess companies, ObjectMapper json) {
    this.register = register;
    this.companies = companies;
    this.json = json;
  }

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
