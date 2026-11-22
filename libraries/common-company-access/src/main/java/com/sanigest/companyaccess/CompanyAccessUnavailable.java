package com.sanigest.companyaccess;

/** An authoritative answer could not be obtained. */
public final class CompanyAccessUnavailable extends RuntimeException {
  public CompanyAccessUnavailable() {
    super("Company authorization is unavailable");
  }
}
