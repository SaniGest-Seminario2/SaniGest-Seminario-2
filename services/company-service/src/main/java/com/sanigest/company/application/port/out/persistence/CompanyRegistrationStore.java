package com.sanigest.company.application.port.out.persistence;

import com.sanigest.company.application.dto.CompanyRegistration;
import com.sanigest.company.domain.model.Company;
import java.util.Optional;
import java.util.UUID;

/** Stores registration state within the transaction opened by the use case. */
public interface CompanyRegistrationStore {
  Optional<CompanyRegistration> find(UUID userId, String key);

  boolean lockActiveMembership(UUID userId, UUID companyId);

  boolean hasMembership(UUID userId);

  Company loadCompany(UUID companyId);

  void insert(UUID userId, String key, CompanyRegistration registration, Company company);
}
