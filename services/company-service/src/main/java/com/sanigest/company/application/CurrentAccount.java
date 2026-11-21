package com.sanigest.company.application;

/** Resolves the current account from Identity using the caller's authenticated access token. */
public interface CurrentAccount {
  AccountType resolve(String accessToken);

  enum AccountType {
    REGULAR,
    ADVISOR
  }

  class AuthorizationUnavailableException extends RuntimeException {}
}
