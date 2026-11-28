package com.sanigest.company.application.port.out.persistence.profile;

import com.sanigest.company.domain.model.profile.Company;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence port owned by Company. */
public interface CompanyRepository {
  Optional<Company> findById(UUID id);

  Optional<Company> findByIdForUpdate(UUID id);

  Instant updateProfile(Company company);
}
