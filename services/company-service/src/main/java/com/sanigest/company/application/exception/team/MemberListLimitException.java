package com.sanigest.company.application.exception.team;

public class MemberListLimitException extends RuntimeException {
  public MemberListLimitException(int limit) {
    super("More than " + limit + " members match. Refine the search or use view=page.");
  }
}
