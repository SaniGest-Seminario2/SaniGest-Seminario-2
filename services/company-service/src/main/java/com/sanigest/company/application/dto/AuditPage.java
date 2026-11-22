package com.sanigest.company.application.dto;

import com.sanigest.company.domain.model.CompanyAuditEvent;
import java.util.List;

public record AuditPage(List<CompanyAuditEvent> content, int page, int size, boolean hasNext) {}
