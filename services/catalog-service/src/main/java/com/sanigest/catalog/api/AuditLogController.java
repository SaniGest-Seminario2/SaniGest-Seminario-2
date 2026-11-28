package com.sanigest.catalog.api;

import com.sanigest.companyaccess.CompanyAccess;
import com.sanigest.companyaccess.CompanyAccessUnavailable;
import com.sanigest.security.CompanyAuditEndpoint;
import com.sanigest.web.audit.AuditPage.Reader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/catalog/audit-log")
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
