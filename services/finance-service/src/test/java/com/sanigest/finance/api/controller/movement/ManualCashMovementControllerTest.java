package com.sanigest.finance.api.controller.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.companyaccess.application.port.out.CompanyAccess;
import com.sanigest.finance.api.security.CashAccess;
import com.sanigest.finance.application.dto.movement.ManualMovement.Document;
import com.sanigest.finance.application.dto.movement.ManualMovement.Options;
import com.sanigest.finance.application.dto.movement.ManualMovement.Result;
import com.sanigest.finance.application.exception.movement.InvalidManualMovement;
import com.sanigest.finance.application.usecase.movement.ManualCashMovements;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class ManualCashMovementControllerTest {
  private final UUID company = UUID.randomUUID();
  private final UUID operator = UUID.randomUUID();

  @Test
  void manualMovementsAuthorizeTheSelectedCompanyAndProtectDocuments() {
    var access = mock(CompanyAccess.class);
    var movements = mock(ManualCashMovements.class);
    var controller =
        new ManualCashMovementController(movements, new CashAccess(accessProvider(access)));
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
    var options = new Options(List.of(), List.of(), List.of(), List.of(), null);
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
    var controller =
        new ManualCashMovementController(movements, new CashAccess(accessProvider(access)));
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
}
