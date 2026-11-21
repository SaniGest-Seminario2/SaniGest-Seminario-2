package com.sanigest.company.application;

import com.sanigest.company.domain.model.MembershipStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetMyCompanyUseCase {
  private final MembershipRepository memberships;

  public GetMyCompanyUseCase(MembershipRepository memberships) {
    this.memberships = memberships;
  }

  @Transactional(readOnly = true)
  public CompanyProfile get(UUID userId) {
    var membership =
        memberships.findCompanyByUserId(userId).orElseThrow(CompanyNotFoundException::new);
    if (membership.status() != MembershipStatus.ACTIVE) {
      throw new CompanyAccessDeniedException();
    }
    return membership.company();
  }

  public interface MembershipRepository {
    Optional<CompanyMembership> findCompanyByUserId(UUID userId);
  }

  public record CompanyMembership(MembershipStatus status, CompanyProfile company) {}

  public record CompanyProfile(
      UUID companyId,
      String companyName,
      String businessName,
      String rnc,
      String location,
      String currency,
      String status) {}

  public static class CompanyNotFoundException extends RuntimeException {}

  public static class CompanyAccessDeniedException extends RuntimeException {}
}
