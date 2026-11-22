package com.sanigest.company.application.dto;

import com.sanigest.company.domain.model.Company;
import java.time.Instant;

public record UpdatedCompany(Company company, Instant updatedAt) {}
