package com.sanigest.sales.infrastructure.integration.finance;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.sanigest.sales.application.exception.InvoicePaymentConflict;
import com.sanigest.sales.application.exception.PaymentAdmissionUnavailable;
import com.sanigest.sales.application.port.out.integration.PaymentAdmission;
import com.sanigest.sales.domain.enums.PaymentMethod;
import com.sanigest.sales.domain.model.InvoicePayment;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mock.env.MockEnvironment;

class FinanceAdmissionConfigurationTest {
  @RegisterExtension
  static final WireMockExtension FINANCE =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private final UUID company = UUID.randomUUID();
  private final UUID operator = UUID.randomUUID();
  private final UUID shift = UUID.randomUUID();
  private final UUID account = UUID.randomUUID();
  private final String path =
      "/internal/companies/" + company + "/operators/" + operator + "/active-shift";

  @Test
  void resolvesAccountsForEveryLineUsingCompanyAndOperator() {
    respond(
        200,
        """
        {"shiftId":"%s","accountsByMethod":{"CASH":"%s"}}
        """
            .formatted(shift, account));

    assertThat(admit(PaymentMethod.CASH, PaymentMethod.CASH))
        .isEqualTo(new PaymentAdmission.Admission(shift, Map.of(0, account, 1, account)));
    assertThatThrownBy(() -> admit(PaymentMethod.CASH, PaymentMethod.TRANSFER))
        .isInstanceOf(InvoicePaymentConflict.class);
  }

  @Test
  void distinguishesMissingShiftFromUnavailableFinance() {
    respond(404, "");
    assertThatThrownBy(() -> admit(PaymentMethod.CASH)).isInstanceOf(InvoicePaymentConflict.class);

    respond(503, "");
    assertThatThrownBy(() -> admit(PaymentMethod.CASH))
        .isInstanceOf(PaymentAdmissionUnavailable.class);
  }

  private void respond(int status, String body) {
    FINANCE.stubFor(
        get(path)
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }

  private PaymentAdmission.Admission admit(PaymentMethod... methods) {
    var environment = new MockEnvironment();
    environment.setActiveProfiles("test");
    var configuration = new FinanceAdmissionConfiguration();
    var shifts =
        configuration.financeActiveCashShifts(
            URI.create(FINANCE.baseUrl()), "test-token", environment);
    var client = configuration.financePaymentAdmission(shifts);
    var lines =
        List.of(methods).stream()
            .map(method -> new InvoicePayment.Line(method, BigDecimal.ONE, null))
            .toList();
    return client.admit(
        company,
        operator,
        UUID.randomUUID(),
        new InvoicePayment.Request("Cliente", BigDecimal.TEN, lines, null));
  }
}
