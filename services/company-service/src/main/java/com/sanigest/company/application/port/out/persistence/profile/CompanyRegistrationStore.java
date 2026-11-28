package com.sanigest.company.application.port.out.persistence.profile;

import com.sanigest.company.application.dto.profile.CompanyRegistration;
import com.sanigest.company.domain.model.profile.Company;
import com.sanigest.company.domain.model.team.Membership;
import java.util.Optional;
import java.util.UUID;

/** Stores registration state within the transaction opened by the use case. */
public interface CompanyRegistrationStore {
  /** Serializes membership creation for this actor until the transaction ends. */
  void lockMembershipCreation(UUID userId);

  Optional<CompanyRegistration> find(UUID userId, String key);

  boolean lockActiveMembership(UUID userId, UUID companyId);

  boolean hasMembership(UUID userId);

  Company loadCompany(UUID companyId);

  void insert(String key, String requestHash, Company company, Membership owner);
}
