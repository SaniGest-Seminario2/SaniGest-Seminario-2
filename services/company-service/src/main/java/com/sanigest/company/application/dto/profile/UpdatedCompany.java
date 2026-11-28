package com.sanigest.company.application.dto.profile;

import com.sanigest.company.domain.model.profile.Company;
import java.time.Instant;

public record UpdatedCompany(Company company, Instant updatedAt) {}
