package com.sanigest.company.domain.port;

import com.sanigest.company.domain.model.CompanyAuditEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CompanyAuditRepository {
  void append(CompanyAuditEvent event);

  List<CompanyAuditEvent> findPage(
      UUID companyId, Instant from, Instant to, long offset, int limit);
}
