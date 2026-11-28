package com.sanigest.finance.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.companyaccess.CompanyAccess;
import com.sanigest.finance.application.ActiveCashSummary;
import com.sanigest.finance.application.PaymentAdmission;
import com.sanigest.finance.application.PaymentAdmission.ActiveShift;
import com.sanigest.finance.infrastructure.FinancialProjections;
import com.sanigest.security.CompanyAuditParameters;
import com.sanigest.web.audit.AuditPage;
import com.sanigest.web.audit.AuditPage.Reader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class FinanceControllersTest {
  private final UUID company = UUID.randomUUID();
  private final UUID operator = UUID.randomUUID();
  private final Jwt jwt =
      Jwt.withTokenValue("token").header("alg", "RS256").subject(operator.toString()).build();

  @Test
  void projectionsRequireCompanyAndForwardIdentity() {
    var projections = mock(FinancialProjections.class);
    var controller = new FinancialProjectionController(projections);
    assertEquals(
        400,
        assertThrows(
                ResponseStatusException.class, () -> controller.projection(new HttpHeaders(), jwt))
            .getStatusCode()
            .value());
    verifyNoInteractions(projections);
    when(projections.read(company, "token")).thenReturn("{\"mode\":\"read\"}");
    when(projections.rebuild(company, "token")).thenReturn("{\"mode\":\"rebuild\"}");
    assertEquals("{\"mode\":\"read\"}", controller.projection(headers(), jwt));
    assertEquals("{\"mode\":\"rebuild\"}", controller.rebuild(headers(), jwt));
  }

  @Test
  void activeSummaryUsesSelectedCompanyAndAuthenticatedOperator() {
    var summary = mock(ActiveCashSummary.class);
    var controller = new ActiveCashSummaryController(summary);
    assertEquals(
        400,
        assertThrows(
                ResponseStatusException.class,
                () -> controller.activeSummary(jwt, new HttpHeaders()))
            .getStatusCode()
            .value());
    verifyNoInteractions(summary);
    when(summary.find(company, operator, "token")).thenReturn(Optional.empty());
    assertEquals(404, controller.activeSummary(jwt, headers()).getStatusCode().value());
  }

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

  @Test
  void paymentAdmissionRequiresConfiguredToken() {
    var admission = mock(PaymentAdmission.class);
    var controller = new PaymentAdmissionController(admission, "secret");
    assertEquals(
        401,
        assertThrows(
                ResponseStatusException.class,
                () -> controller.activeShift(company, operator, "Bearer wrong"))
            .getStatusCode()
            .value());
    assertEquals(
        401,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    new PaymentAdmissionController(admission, "")
                        .activeShift(company, operator, ""))
            .getStatusCode()
            .value());
    verifyNoInteractions(admission);
    var shift = new ActiveShift(UUID.randomUUID(), Map.of());
    when(admission.find(company, operator)).thenReturn(Optional.of(shift));
    assertEquals(shift, controller.activeShift(company, operator, "Bearer secret").getBody());
  }

  private HttpHeaders headers() {
    var headers = new HttpHeaders();
    headers.set("X-Company-Id", company.toString());
    return headers;
  }
}
