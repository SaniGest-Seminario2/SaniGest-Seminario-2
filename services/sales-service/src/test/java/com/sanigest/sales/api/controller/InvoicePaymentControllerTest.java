package com.sanigest.sales.api.controller;

import static com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission.SALES_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.sales.application.exception.SalesAccessForbidden;
import com.sanigest.sales.application.port.out.integration.PaymentAdmission;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.InvoicePayments;
import com.sanigest.sales.application.usecase.invoice.RegisterInvoicePayment;
import com.sanigest.sales.domain.enums.PaymentMethod;
import com.sanigest.sales.domain.model.InvoicePayment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;

class InvoicePaymentControllerTest {
  private final UUID invoice = UUID.randomUUID();
  private final UUID company = UUID.randomUUID();
  private final UUID operator = UUID.randomUUID();
  private final SalesAuthorization authorization = mock(SalesAuthorization.class);
  private final InvoicePayments payments = mock(InvoicePayments.class);
  private final PaymentAdmission admission = mock(PaymentAdmission.class);
  private final InvoicePaymentController controller =
      new InvoicePaymentController(
          new RegisterInvoicePayment(authorization, () -> operator, payments, admission));
  private final InvoicePayment.Request request =
      new InvoicePayment.Request(
          "Cliente",
          new BigDecimal("20.00"),
          List.of(new InvoicePayment.Line(PaymentMethod.CASH, BigDecimal.ONE, null)),
          null);

  @Test
  void registersUsingTheAuthorizedCompanyAndAuthenticatedOperator() {
    var line =
        new InvoicePayment.AppliedLine(UUID.randomUUID(), PaymentMethod.CASH, BigDecimal.ONE, null);
    var result =
        new InvoicePayment.Result(
            UUID.randomUUID(),
            invoice,
            UUID.randomUUID(),
            operator,
            UUID.randomUUID(),
            Instant.EPOCH,
            BigDecimal.ONE,
            new BigDecimal("19.00"),
            "DOP",
            List.of(line),
            null);
    when(authorization.requireMembership(SALES_WRITE)).thenReturn(company);
    when(payments.register(company, operator, invoice, "payment-1", request, admission))
        .thenReturn(result);

    assertThat(controller.register(invoice, headers("payment-1"), request)).isSameAs(result);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "has spaces", "a,b"})
  void rejectsMalformedKeys(String key) {
    assertThatThrownBy(() -> controller.register(invoice, headers(key), request))
        .isInstanceOf(InvalidIdempotencyKey.class);
    verifyNoInteractions(authorization, payments);
  }

  @Test
  void rejectsAbsentAndRepeatedKeys() {
    assertThatThrownBy(() -> controller.register(invoice, new HttpHeaders(), request))
        .isInstanceOf(InvalidIdempotencyKey.class);
    var repeated = headers("first");
    repeated.add("Idempotency-Key", "second");
    assertThatThrownBy(() -> controller.register(invoice, repeated, request))
        .isInstanceOf(InvalidIdempotencyKey.class);
    verifyNoInteractions(authorization, payments);
  }

  @Test
  void rejectsAnOperatorWithoutWritePermission() {
    when(authorization.requireMembership(SALES_WRITE)).thenThrow(new SalesAccessForbidden());
    assertThatThrownBy(() -> controller.register(invoice, headers("payment-1"), request))
        .isInstanceOf(SalesAccessForbidden.class);
    verifyNoInteractions(payments, admission);
  }

  private static HttpHeaders headers(String key) {
    var headers = new HttpHeaders();
    headers.add("Idempotency-Key", key);
    return headers;
  }
}
