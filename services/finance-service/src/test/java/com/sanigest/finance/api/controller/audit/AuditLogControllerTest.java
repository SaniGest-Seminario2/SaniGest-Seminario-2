package com.sanigest.finance.api.controller.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.companyaccess.application.port.out.CompanyAccess;
import com.sanigest.security.api.dto.audit.CompanyAuditParameters;
import com.sanigest.web.application.dto.audit.AuditPage;
import com.sanigest.web.application.dto.audit.AuditPage.Reader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ResponseStatusException;

class AuditLogControllerTest {
  private final UUID company = UUID.randomUUID();

  @Test
  void financeAuditRequiresCompanyAuthorizationBeforeReading() {
    var access = mock(CompanyAccess.class);
    var reader = mock(Reader.class);
    var query = new CompanyAuditParameters(null, null, null, null, null, null, null, null);
    var controller = new AuditLogController(accessProvider(null), reader);
    assertEquals(
        503,
        assertThrows(ResponseStatusException.class, () -> controller.get(headers(), query))
            .getStatusCode()
            .value());
    var configured = new AuditLogController(accessProvider(access), reader);
    assertEquals(
        403,
        assertThrows(ResponseStatusException.class, () -> configured.get(headers(), query))
            .getStatusCode()
            .value());
    verifyNoInteractions(reader);

    var page = new AuditPage(List.of(), 0, 20, false);
    when(access.canReadAudit(company)).thenReturn(true);
    when(reader.read(company, query.filters())).thenReturn(page);
    assertEquals(page, configured.get(headers(), query).getBody());
  }

  private static ObjectProvider<CompanyAccess> accessProvider(CompanyAccess access) {
    return new ObjectProvider<>() {
      @Override
      public CompanyAccess getIfAvailable() {
        return access;
      }
    };
  }

  private HttpHeaders headers() {
    var headers = new HttpHeaders();
    headers.set("X-Company-Id", company.toString());
    return headers;
  }
}
