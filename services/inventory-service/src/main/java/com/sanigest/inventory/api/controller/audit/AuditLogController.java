package com.sanigest.inventory.api.controller.audit;

import com.sanigest.companyaccess.application.exception.CompanyAccessUnavailable;
import com.sanigest.companyaccess.application.port.out.CompanyAccess;
import com.sanigest.security.api.controller.audit.CompanyAuditEndpoint;
import com.sanigest.web.application.dto.audit.AuditPage.Reader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/inventory/audit-log")
class AuditLogController extends CompanyAuditEndpoint {
  AuditLogController(ObjectProvider<CompanyAccess> access, Reader entries) {
    super(
        company -> {
          try {
            var authorizer = access.getIfAvailable();
            if (authorizer == null) {
              throw new CompanyAccessUnavailable();
            }
            return authorizer.canReadAudit(company);
          } catch (CompanyAccessUnavailable unavailable) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Company authorizer unavailable");
          }
        },
        entries);
  }
}
