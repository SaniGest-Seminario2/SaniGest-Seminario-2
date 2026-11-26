package com.sanigest.expense.api;

import com.sanigest.companyaccess.AuthenticatedActor;
import com.sanigest.companyaccess.CompanyAccess;
import com.sanigest.companyaccess.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.CompanyAccessUnavailable;
import com.sanigest.expense.application.ExpenseFailure;
import com.sanigest.expense.application.ExpenseRegistration.Document;
import com.sanigest.expense.application.ExpenseRegistration.Request;
import com.sanigest.expense.application.ExpenseStore;
import com.sanigest.expense.application.RegisterExpense;
import com.sanigest.security.CompanySelector;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
class ExpenseController {
  private final RegisterExpense register;
  private final ObjectProvider<CompanyAccess> companies;
  private final ObjectMapper json;

  ExpenseController(
      RegisterExpense register, ObjectProvider<CompanyAccess> companies, ObjectMapper json) {
    this.register = register;
    this.companies = companies;
    this.json = json;
  }

  @PostMapping("/api/v1/expenses")
  @ResponseStatus(HttpStatus.CREATED)
  Document create(HttpServletRequest http, @Valid @RequestBody Request request) {
    var company =
        CompanySelector.resolve(
            Collections.list(http.getHeaders(CompanySelector.HEADER)), null, true);
    var actor = AuthenticatedActor.userId().orElseThrow(ExpenseController::denied);
    requireWrite(company);
    var keys = Collections.list(http.getHeaders("Idempotency-Key"));
    if (keys.size() != 1 || !keys.getFirst().matches("[A-Za-z0-9._:-]{1,128}")) {
      throw ExpenseFailure.invalid("EXPENSE_INVALID_IDEMPOTENCY_KEY");
    }
    return register.register(
        new ExpenseStore.Key(company, actor, keys.getFirst()), hash(request), request);
  }

  private void requireWrite(UUID companyId) {
    try {
      companies
          .getIfAvailable(() -> {
            throw unavailable();
          })
          .membership(companyId, CompanyAccess.Scope.WRITE)
          .filter(member -> companyId.equals(member.companyId()))
          .filter(member ->
              member.role() == MembershipRole.OWNER || member.role() == MembershipRole.ADMIN)
          .orElseThrow(ExpenseController::denied);
    } catch (CompanyAccessUnavailable unavailable) {
      throw unavailable();
    }
  }

  private String hash(Request request) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static ExpenseFailure denied() {
    return new ExpenseFailure(ExpenseFailure.Kind.FORBIDDEN, "EXPENSE_ACCESS_DENIED");
  }

  private static ExpenseFailure unavailable() {
    return new ExpenseFailure(ExpenseFailure.Kind.UNAVAILABLE, "EXPENSE_AUTHORIZATION_UNAVAILABLE");
  }
}
