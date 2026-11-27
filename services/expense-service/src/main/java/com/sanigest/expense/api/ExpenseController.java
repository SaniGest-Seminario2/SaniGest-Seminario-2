package com.sanigest.expense.api;

import com.sanigest.companyaccess.AuthenticatedActor;
import com.sanigest.companyaccess.CompanyAccess.Scope;
import com.sanigest.expense.application.ExpenseFailure;
import com.sanigest.expense.application.ExpenseRegistration.Document;
import com.sanigest.expense.application.ExpenseRegistration.Request;
import com.sanigest.expense.application.ExpenseStore;
import com.sanigest.expense.application.RegisterExpense;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
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
  Document create(HttpServletRequest http, @Valid @RequestBody Request request) {
    var company = ExpenseCompanyAccess.select(http);
    var actor = AuthenticatedActor.userId().orElseThrow(ExpenseCompanyAccess::denied);
    companies.require(company, Scope.WRITE);
    return register.register(
        new ExpenseStore.Key(company, actor, idempotencyKey(http)), hash(request), request);
  }

  static String idempotencyKey(HttpServletRequest http) {
    var keys = Collections.list(http.getHeaders("Idempotency-Key"));
    if (keys.size() != 1 || !keys.getFirst().matches("[A-Za-z0-9._:-]{1,128}")) {
      throw ExpenseFailure.invalid("EXPENSE_INVALID_IDEMPOTENCY_KEY");
    }
    return keys.getFirst();
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
