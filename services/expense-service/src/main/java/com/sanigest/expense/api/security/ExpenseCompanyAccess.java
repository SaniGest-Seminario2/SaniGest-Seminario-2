package com.sanigest.expense.api.security;

import com.sanigest.companyaccess.CompanyAccess;
import com.sanigest.companyaccess.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.CompanyAccess.Scope;
import com.sanigest.companyaccess.CompanyAccessUnavailable;
import com.sanigest.expense.application.exception.ExpenseFailure;
import com.sanigest.security.CompanySelector;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class ExpenseCompanyAccess {
  private final ObjectProvider<CompanyAccess> companies;

  public ExpenseCompanyAccess(ObjectProvider<CompanyAccess> companies) {
    this.companies = companies;
  }

  public UUID require(HttpServletRequest http, Scope scope) {
    var companyId = select(http);
    require(companyId, scope);
    return companyId;
  }

  public static UUID select(HttpServletRequest http) {
    return CompanySelector.resolve(
        Collections.list(http.getHeaders(CompanySelector.HEADER)), null, true);
  }

  public void require(UUID companyId, Scope scope) {
    try {
      companies
          .getIfAvailable(() -> {
            throw unavailable();
          })
          .membership(companyId, scope)
          .filter(member -> companyId.equals(member.companyId()))
          .filter(member ->
              member.role() == MembershipRole.OWNER
                  || member.role() == MembershipRole.ADMIN
                  || (scope == Scope.READ && member.explicitGrant()))
          .orElseThrow(ExpenseCompanyAccess::denied);
    } catch (CompanyAccessUnavailable unavailable) {
      throw unavailable();
    }
  }

  public static ExpenseFailure denied() {
    return new ExpenseFailure(ExpenseFailure.Kind.FORBIDDEN, "EXPENSE_ACCESS_DENIED");
  }

  private static ExpenseFailure unavailable() {
    return new ExpenseFailure(ExpenseFailure.Kind.UNAVAILABLE, "EXPENSE_AUTHORIZATION_UNAVAILABLE");
  }
}
