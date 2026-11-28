package com.sanigest.company.application.port.out.persistence.advisor;

import com.sanigest.company.application.dto.advisor.AuthorizedAdvisorCompany;
import com.sanigest.company.domain.model.advisor.AdvisorAccess;
import com.sanigest.company.domain.model.advisor.AdvisorPermission;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AdvisorAccessRepository {
  List<AdvisorAccess> list(UUID companyId);

  List<AuthorizedAdvisorCompany> listAuthorizedCompanies(UUID advisorId);

  AdvisorAccess find(UUID companyId, UUID advisorId);

  boolean allows(UUID companyId, UUID advisorId, AdvisorPermission permission);

  void saveRevision(UUID companyId, UUID advisorId, long previousRevision);

  void grant(UUID companyId, UUID advisorId, AdvisorPermission permission, Instant at);

  void revoke(UUID companyId, UUID advisorId, AdvisorPermission permission, Instant at);
}
