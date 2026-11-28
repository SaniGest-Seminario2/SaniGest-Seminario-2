package com.sanigest.catalog.infrastructure.persistence.audit;

import com.sanigest.web.application.dto.audit.AuditPage;
import com.sanigest.web.application.dto.audit.AuditPage.Filters;
import com.sanigest.web.infrastructure.persistence.audit.JdbcAuditReader;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AuditEntries implements AuditPage.Reader {
  private final JdbcAuditReader reader;

  AuditEntries(JdbcTemplate jdbc) {
    reader = new JdbcAuditReader(jdbc, "catalog.audit_entries");
  }

  @Override
  public AuditPage read(UUID company, Filters filters) {
    return reader.read(company, filters);
  }
}
