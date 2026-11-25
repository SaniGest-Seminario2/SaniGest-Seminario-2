package com.sanigest.company.application.dto;

import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import java.util.Locale;
import java.util.Set;

public record MemberSearchCriteria(
    String search, MembershipStatus status, CompanyRole role, int page, int size, String sort) {
  public MemberSearchCriteria {
    search = search == null ? "" : search.strip();
    if (search.length() > 160
        || page < 0
        || size < 1
        || size > 100
        || !Set.of("membershipId,asc", "membershipId,desc").contains(sort)) {
      throw new IllegalArgumentException("Invalid member page parameters");
    }
    search = search.toLowerCase(Locale.ROOT);
  }

  public boolean descending() {
    return "membershipId,desc".equals(sort);
  }

  public long offset() {
    return (long) page * size;
  }
}
