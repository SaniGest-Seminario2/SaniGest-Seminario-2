package com.sanigest.company.domain.port;

import com.sanigest.company.domain.model.Company;
import java.util.Optional;
import java.util.UUID;

/** Persistence port owned by Company. */
public interface CompanyRepository {
  Optional<Company> findById(UUID id);
}
