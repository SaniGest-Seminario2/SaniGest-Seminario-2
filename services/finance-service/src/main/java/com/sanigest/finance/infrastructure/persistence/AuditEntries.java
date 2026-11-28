package com.sanigest.finance.infrastructure.persistence;

import com.sanigest.web.audit.AuditPage;
import com.sanigest.web.audit.AuditPage.Filters;
import com.sanigest.web.audit.JdbcAuditReader;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AuditEntries implements AuditPage.Reader {
  private final JdbcAuditReader reader;

  AuditEntries(JdbcTemplate jdbc) {
    reader = new JdbcAuditReader(jdbc, "finance.audit_entries");
  }

  @Override
  public AuditPage read(UUID company, Filters filters) {
    return reader.read(company, filters);
  }
}
