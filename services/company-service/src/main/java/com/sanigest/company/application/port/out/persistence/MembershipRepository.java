package com.sanigest.company.application.port.out.persistence;

import com.sanigest.company.application.dto.CompanyMembership;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.model.Membership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Membership persistence and the associated company profile projection. */
public interface MembershipRepository {
  Optional<CompanyMembership> findCompanyByUserId(UUID userId);

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
