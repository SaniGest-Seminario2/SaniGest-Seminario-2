package com.sanigest.company.application.port.out.persistence.team;

import com.sanigest.company.application.dto.access.AuthorizedCompany;
import com.sanigest.company.application.dto.team.CompanyMembership;
import com.sanigest.company.domain.enums.team.CompanyRole;
import com.sanigest.company.domain.enums.team.MembershipStatus;
import com.sanigest.company.domain.model.team.Membership;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Membership persistence and the associated company profile projection. */
public interface MembershipRepository {
  List<AuthorizedCompany> findAuthorizedCompanies(UUID userId);

  /** Serializes registration and invitation acceptance for this user in the current transaction. */
  void lockMembershipCreation(UUID userId);

  boolean lockActiveMembership(UUID companyId, UUID userId);

  /** Resolves a sole operational membership for a legacy public route. */
  Optional<CompanyMembership> findCompanyByUserId(UUID userId);

  Optional<CompanyMembership> findCompanyByUserIdAndCompanyId(UUID userId, UUID companyId);

  Optional<Membership> findActiveMembershipForUpdate(UUID userId, UUID companyId);

  /** Resolves access for the specific operational company and user. */
  Optional<ActiveAccess> findActiveAccess(UUID userId, UUID companyId, String permission);

  record ActiveAccess(
      UUID companyId, CompanyRole role, boolean explicitGrant, String companyName) {}

  /** Resolves and locks an operational membership for a legacy public write. */
  Optional<Membership> findByUserIdForUpdate(UUID userId);

  List<Membership> findAllByCompanyId(UUID companyId);

  void lockMembershipChanges(UUID companyId);

  Optional<Membership> findByCompanyIdAndUserId(UUID companyId, UUID userId);

  boolean hasPermission(Membership membership, String permission);

  boolean hasOtherActiveOwner(UUID companyId, UUID userId);

  void updateRole(Membership membership, CompanyRole role);

  void revoke(Membership membership, Instant now);

  boolean revokedBefore(UUID companyId, UUID userId, Instant invitationCreatedAt);

  boolean existsByUserId(UUID userId);

  boolean existsByUserIdOutsideCompany(UUID userId, UUID companyId);

  void reinstate(Membership membership);

  void updateStatus(Membership membership, MembershipStatus status);

  List<Membership> findBatch(
      UUID companyId,
      CompanyRole role,
      Set<MembershipStatus> statuses,
      boolean descending,
      UUID after,
      int limit,
      long offset);

  long count(UUID companyId, CompanyRole role, Set<MembershipStatus> statuses);

  void insert(Membership membership);
}
