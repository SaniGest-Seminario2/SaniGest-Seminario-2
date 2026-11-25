package com.sanigest.company.application.port.out.persistence;

import com.sanigest.company.application.dto.AuthorizedCompany;
import com.sanigest.company.application.dto.CompanyMembership;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.model.Membership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Membership persistence and the associated company profile projection. */
public interface MembershipRepository {
  List<AuthorizedCompany> findAuthorizedCompanies(UUID userId);

  /** Serializes registration and invitation acceptance for this user in the current transaction. */
  void lockMembershipCreation(UUID userId);

  boolean lockActiveMembership(UUID companyId, UUID userId);

  /** Resolves a sole operational membership for a legacy public route. */
  Optional<CompanyMembership> findCompanyByUserId(UUID userId);

  /** Returns empty when an implicit request has zero or several operational companies. */
  Optional<ActiveAccess> findActiveAccess(UUID userId, String permission);

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

  void revoke(Membership membership);

  boolean existsByUserId(UUID userId);

  void insert(Membership membership);
}
