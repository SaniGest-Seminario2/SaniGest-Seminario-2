package com.sanigest.expense.api;

import com.sanigest.companyaccess.CompanyAccess;
import com.sanigest.companyaccess.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.CompanyAccess.Scope;
import com.sanigest.companyaccess.CompanyAccessUnavailable;
import com.sanigest.expense.application.ExpenseFailure;
import com.sanigest.security.CompanySelector;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
class ExpenseCompanyAccess {
  private final ObjectProvider<CompanyAccess> companies;

  ExpenseCompanyAccess(ObjectProvider<CompanyAccess> companies) {
    this.companies = companies;
  }

  UUID require(HttpServletRequest http, Scope scope) {
    var companyId = select(http);
    require(companyId, scope);
    return companyId;
  }

  static UUID select(HttpServletRequest http) {
    return CompanySelector.resolve(
        Collections.list(http.getHeaders(CompanySelector.HEADER)), null, true);
  }

  void require(UUID companyId, Scope scope) {
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

  static ExpenseFailure denied() {
    return new ExpenseFailure(ExpenseFailure.Kind.FORBIDDEN, "EXPENSE_ACCESS_DENIED");
  }

  private static ExpenseFailure unavailable() {
    return new ExpenseFailure(ExpenseFailure.Kind.UNAVAILABLE, "EXPENSE_AUTHORIZATION_UNAVAILABLE");
  }
}
