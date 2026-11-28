package com.sanigest.finance.api.security;

import com.sanigest.companyaccess.application.exception.CompanyAccessUnavailable;
import com.sanigest.companyaccess.application.port.out.CompanyAccess;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.Scope;
import com.sanigest.security.api.request.company.CompanySelector;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CashAccess {
  private final ObjectProvider<CompanyAccess> companies;

  public CashAccess(ObjectProvider<CompanyAccess> companies) {
    this.companies = companies;
  }

  public UUID require(List<String> companyHeaders, Scope scope) {
    try {
      UUID company = CompanySelector.resolve(companyHeaders, null, true);
      companies
          .getIfAvailable(() -> {
            throw new CompanyAccessUnavailable();
          })
          .membership(company, scope)
          .filter(member -> company.equals(member.companyId()))
          .filter(member ->
              switch (member.role()) {
                case OWNER, ADMIN -> true;
                case OPERATOR -> member.explicitGrant();
                case VIEWER -> scope == Scope.READ && member.explicitGrant();
              })
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
      return company;
    } catch (CompanySelector.InvalidContext invalid) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
    } catch (CompanyAccessUnavailable unavailable) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Company authorizer unavailable");
    }
  }
}
