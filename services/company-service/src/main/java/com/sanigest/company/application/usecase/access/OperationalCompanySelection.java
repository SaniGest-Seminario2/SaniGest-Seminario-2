package com.sanigest.company.application.usecase.access;

import com.sanigest.company.application.dto.team.CompanyMembership;
import com.sanigest.company.application.exception.access.CompanyAccessDeniedException;
import com.sanigest.company.application.port.out.persistence.team.MembershipRepository;
import com.sanigest.company.domain.model.team.Membership;
import java.util.Optional;
import java.util.UUID;

/** Restricts singular compatibility to operations on a user's own company. */
public final class OperationalCompanySelection {
  private OperationalCompanySelection() {}

  public static Optional<CompanyMembership> profile(
      MembershipRepository memberships, UUID actor, UUID requestedCompany) {
    return requestedCompany == null
        ? memberships.findCompanyByUserId(actor)
        : Optional.of(
            memberships
                .findCompanyByUserIdAndCompanyId(actor, requestedCompany)
                .orElseThrow(CompanyAccessDeniedException::new));
  }

  public static Optional<Membership> lockMembership(
      MembershipRepository memberships, UUID actor, UUID requestedCompany) {
    return requestedCompany == null
        ? memberships.findByUserIdForUpdate(actor)
        : Optional.of(
            memberships
                .findActiveMembershipForUpdate(actor, requestedCompany)
                .orElseThrow(CompanyAccessDeniedException::new));
  }
}
