package com.sanigest.company.application.port.out.persistence;

import com.sanigest.company.domain.model.CompanyAuditEvent;
import com.sanigest.web.audit.AuditPage.Filters;
import java.util.List;
import java.util.UUID;

public interface CompanyAuditRepository {
  void append(CompanyAuditEvent event);

  List<CompanyAuditEvent> findPage(UUID companyId, Filters filters);
}
