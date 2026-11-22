package com.sanigest.company.application.port.out.integration;

import com.sanigest.company.application.account.AccountType;

/** Resolves the current account from Identity using the caller's authenticated access token. */
public interface CurrentAccount {
  AccountType resolve(String accessToken);
}
