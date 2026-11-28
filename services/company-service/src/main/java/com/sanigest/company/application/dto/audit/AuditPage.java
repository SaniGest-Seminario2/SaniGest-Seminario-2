package com.sanigest.company.application.dto.audit;

import com.sanigest.company.domain.model.audit.CompanyAuditEvent;
import java.util.List;

public record AuditPage(List<CompanyAuditEvent> content, int page, int size, boolean hasNext) {}
