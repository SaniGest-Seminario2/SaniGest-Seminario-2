package com.sanigest.company.application.port.out.persistence.audit;

import com.sanigest.company.domain.model.audit.CompanyAuditEvent;
import com.sanigest.web.application.dto.audit.AuditPage.Filters;
import java.util.List;
import java.util.UUID;

public interface CompanyAuditRepository {
  void append(CompanyAuditEvent event);

  List<CompanyAuditEvent> findPage(UUID companyId, Filters filters);
}
