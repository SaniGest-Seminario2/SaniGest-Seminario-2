package com.sanigest.company.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanigest.company.domain.model.Company;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanyDomainTest {
  @Test
  void rejectsBlankCompanyNames() {
    assertThatThrownBy(() -> new Company(UUID.randomUUID(), " ", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
