package com.sanigest.company.application.usecase.access;

import com.sanigest.company.application.account.AccountType;
import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.port.out.integration.CurrentAccount;
import org.springframework.stereotype.Service;

@Service
public class RequireRegularAccount {
  private final CurrentAccount accounts;

  public RequireRegularAccount(CurrentAccount accounts) {
    this.accounts = accounts;
  }

  public void check(String accessToken) {
    if (accounts.resolve(accessToken) != AccountType.REGULAR) {
      throw new CompanyAccessDeniedException();
    }
  }
}
