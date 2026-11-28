package com.sanigest.finance.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.companyaccess.CompanyAccess;
import com.sanigest.finance.application.dto.ManualMovement.Document;
import com.sanigest.finance.application.dto.ManualMovement.Options;
import com.sanigest.finance.application.dto.ManualMovement.Result;
import com.sanigest.finance.application.exception.FinancialSourceFailure;
import com.sanigest.finance.application.exception.FinancialSourceFailure.Kind;
import com.sanigest.finance.application.exception.InvalidManualMovement;
import com.sanigest.finance.application.port.in.PaymentAdmission;
import com.sanigest.finance.application.port.in.PaymentAdmission.ActiveShift;
import com.sanigest.finance.application.usecase.ActiveCashSummary;
import com.sanigest.finance.application.usecase.FinancialProjections;
import com.sanigest.finance.application.usecase.ManualCashMovements;
import com.sanigest.security.CompanyAuditParameters;
import com.sanigest.web.audit.AuditPage;
import com.sanigest.web.audit.AuditPage.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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

  @Test
  void mapsSourceFailuresAtTheHttpBoundary() {
    var projections = mock(FinancialProjections.class);
    var controller = new FinancialProjectionController(projections);
    var statuses = Map.of(Kind.UNAUTHENTICATED, 401, Kind.FORBIDDEN, 403, Kind.UNAVAILABLE, 503);
    statuses.forEach((kind, status) -> {
      doThrow(new FinancialSourceFailure(kind, "reason")).when(projections).read(company, "token");
      var failure =
          assertThrows(ResponseStatusException.class, () -> controller.projection(headers(), jwt));
      assertEquals(status.intValue(), failure.getStatusCode().value());
      assertEquals("reason", failure.getReason());
    });
  }

  @Test
  void manualMovementsAuthorizeTheSelectedCompanyAndProtectDocuments() {
    var access = mock(CompanyAccess.class);
    var movements = mock(ManualCashMovements.class);
    var controller = new ManualCashMovementController(movements, accessProvider(access));
    var request = new MockHttpServletRequest();
    request.addHeader("X-Company-Id", company.toString());
    var role =
        new CompanyAccess.MembershipContext(company, CompanyAccess.MembershipRole.VIEWER, true);
    when(access.membership(company, CompanyAccess.Scope.WRITE)).thenReturn(Optional.of(role));
    assertEquals(
        403,
        assertThrows(ResponseStatusException.class, () -> controller.create(request, "key", null))
            .getStatusCode()
            .value());
    verifyNoInteractions(movements);

    when(access.membership(company, CompanyAccess.Scope.READ)).thenReturn(Optional.of(role));
    var options = new Options(List.of(), List.of(), List.of(), List.of());
    when(movements.options(company)).thenReturn(options);
    assertEquals(options, controller.options(request));
    var id = UUID.randomUUID();
    assertEquals(
        404,
        assertThrows(ResponseStatusException.class, () -> controller.document(request, id))
            .getStatusCode()
            .value());
    when(movements.document(company, id))
        .thenReturn(Optional.of(new Document("application/pdf", new byte[] {1})));
    assertEquals(
        "attachment",
        controller.document(request, id).getHeaders().getFirst("Content-Disposition"));
  }

  @Test
  void manualMovementsUseAuthenticatedActorAndValidateMultipartDocuments() {
    var access = mock(CompanyAccess.class);
    var movements = mock(ManualCashMovements.class);
    var controller = new ManualCashMovementController(movements, accessProvider(access));
    var request = new MockHttpServletRequest();
    request.addHeader("X-Company-Id", company.toString());
    request.addHeader("Idempotency-Key", "key");
    when(access.membership(company, CompanyAccess.Scope.WRITE))
        .thenReturn(
            Optional.of(
                new CompanyAccess.MembershipContext(
                    company, CompanyAccess.MembershipRole.ADMIN, false)));
    var token =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject(operator.toString())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(token, List.of()));
    try {
      var file =
          new MockMultipartFile(
              "document",
              "test.pdf",
              "application/pdf",
              "%PDF-test".getBytes(StandardCharsets.US_ASCII));
      var operation = UUID.randomUUID();
      when(movements.register(eq(company), eq(operator), eq("key"), isNull(), any(Document.class)))
          .thenReturn(new Result(operation, "MANUAL", operator, null, true));
      assertEquals(
          "/api/v1/cash-movements/" + operation + "/attachment",
          controller.createWithDocument(request, "key", null, file).documentUrl());
      var saved = ArgumentCaptor.forClass(Document.class);
      verify(movements).register(eq(company), eq(operator), eq("key"), isNull(), saved.capture());
      assertEquals("application/pdf", saved.getValue().contentType());
      var invalid =
          new MockMultipartFile("document", "test.pdf", "application/pdf", new byte[] {1});
      assertEquals(
          400,
          controller
              .invalid(
                  assertThrows(
                      InvalidManualMovement.class,
                      () -> controller.createWithDocument(request, "key", null, invalid)))
              .getStatus());
      assertEquals(
          400,
          controller
              .invalid(
                  assertThrows(
                      InvalidManualMovement.class,
                      () -> controller.create(request, "bad key", null)))
              .getStatus());
      assertEquals(409, controller.conflict().getStatus());
    } finally {
      SecurityContextHolder.clearContext();
    }
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
