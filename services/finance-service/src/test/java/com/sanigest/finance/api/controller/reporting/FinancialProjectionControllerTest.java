package com.sanigest.finance.api.controller.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.finance.application.exception.reporting.FinancialSourceFailure;
import com.sanigest.finance.application.exception.reporting.FinancialSourceFailure.Kind;
import com.sanigest.finance.application.usecase.reporting.FinancialProjections;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class FinancialProjectionControllerTest {
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

  private HttpHeaders headers() {
    var headers = new HttpHeaders();
    headers.set("X-Company-Id", company.toString());
    return headers;
  }
}
