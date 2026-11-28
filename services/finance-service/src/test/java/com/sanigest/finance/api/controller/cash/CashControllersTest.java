package com.sanigest.finance.api.controller.cash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.finance.application.port.in.cash.PaymentAdmission;
import com.sanigest.finance.application.port.in.cash.PaymentAdmission.ActiveShift;
import com.sanigest.finance.application.usecase.cash.ActiveCashSummary;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class CashControllersTest {
  private final UUID company = UUID.randomUUID();
  private final UUID operator = UUID.randomUUID();
  private final Jwt jwt =
      Jwt.withTokenValue("token").header("alg", "RS256").subject(operator.toString()).build();

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
