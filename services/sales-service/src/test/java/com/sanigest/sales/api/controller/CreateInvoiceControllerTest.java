package com.sanigest.sales.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.sales.api.dto.CreateInvoiceRequest;
import com.sanigest.sales.api.dto.CreateInvoiceRequest.Line;
import com.sanigest.sales.application.dto.CreatedSaleResponse;
import com.sanigest.sales.application.port.out.integration.CatalogClient;
import com.sanigest.sales.application.port.out.integration.InvoiceCompanyAccess.CompanyContext;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.InvoiceRepository;
import com.sanigest.sales.application.port.out.persistence.SaleIdempotencyRepository;
import com.sanigest.sales.application.port.out.persistence.SaleIdempotencyRepository.Claim;
import com.sanigest.sales.application.port.out.persistence.SaleIdempotencyRepository.Replay;
import com.sanigest.sales.application.port.out.persistence.SaleRepository;
import com.sanigest.sales.application.usecase.invoice.IssueInvoice;
import com.sanigest.sales.application.usecase.sale.CreateSale;
import com.sanigest.sales.application.usecase.sale.CreateSaleIdempotently;
import com.sanigest.sales.domain.enums.RequestedReceiptType;
import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.model.CatalogCustomerSnapshot;
import com.sanigest.sales.domain.model.CatalogItemSnapshot;
import com.sanigest.sales.domain.model.InternalInvoice;
import com.sanigest.sales.domain.model.Receivable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.ObjectMapper;

class CreateInvoiceControllerTest {
  private final UUID company = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID client = UUID.randomUUID();
  private final UUID item = UUID.randomUUID();
  private final CatalogClient catalog = mock(CatalogClient.class);
  private final SaleRepository sales = mock(SaleRepository.class);
  private final InvoiceRepository invoices = mock(InvoiceRepository.class);
  private final SaleIdempotencyRepository keys = mock(SaleIdempotencyRepository.class);
  private final ObjectMapper json = new ObjectMapper();

  private final CreateInvoiceController controller = controller();

  @Test
  void pricesAndReplaysAnInvoiceThroughTheSalesEngine() throws Exception {
    var claim = new Claim(UUID.randomUUID());
    var keyRequest = ArgumentCaptor.forClass(SaleIdempotencyRepository.Request.class);
    when(keys.resolve(keyRequest.capture())).thenReturn(claim);
    when(keys.complete(any(), eq(claim), any()))
        .thenAnswer(call -> call.<Supplier<CreatedSaleResponse>>getArgument(2).get());
    when(catalog.getCustomer(client, company))
        .thenReturn(
            new CatalogCustomerSnapshot(client, "Cliente", CatalogCustomerSnapshot.Status.ACTIVE));
    when(catalog.getInvoiceItem(item, company))
        .thenReturn(
            new CatalogItemSnapshot(
                item,
                "Servicio",
                CatalogItemSnapshot.Type.SERVICE,
                null,
                new BigDecimal("10.1234"),
                "DOP",
                true,
                new BigDecimal("0.18")));
    var obligation = ArgumentCaptor.forClass(Receivable.class);
    when(sales.register(any(), obligation.capture())).thenAnswer(call -> call.getArgument(0));
    when(invoices.issueForNewSale(any(), eq(2026), any(), any()))
        .thenAnswer(call -> {
          var snapshot = call.<Function<String, InternalInvoice>>getArgument(2);
          return call.<Function<InternalInvoice, String>>getArgument(3)
              .apply(snapshot.apply("FAC-2026-001"));
        });

    var response = controller.create(headers("invoice-1"), request());
    var body = json.readTree(response.getBody());
    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(body.path("number").asString()).isEqualTo("FAC-2026-001");
    assertThat(body.path("total").asString()).isEqualTo("22.72");
    assertThat(body.path("requestedReceiptType").asString()).isEqualTo("FISCAL_CREDIT");
    assertThat(body.path("notes").asString()).isEqualTo("Entrega");
    assertThat(body.path("issueDate").asString()).isEqualTo("2026-11-23");
    assertThat(body.at("/lines/0/unitPrice").asString()).isEqualTo("10.1234");
    assertThat(body.at("/lines/0/discount").asString()).isEqualTo("1.00");
    assertThat(body.at("/lines/0/taxAmount").asString()).isEqualTo("3.47");
    assertThat(obligation.getValue().totalAmount()).isEqualByComparingTo("22.72");
    assertThat(obligation.getValue().dueDate()).isEqualTo(LocalDate.of(2026, 11, 23));

    var recorded = keyRequest.getValue();
    assertThat(recorded.operation()).isEqualTo("POST /api/v1/invoices");
    assertThat(recorded.companyId()).isEqualTo(company);
    when(keys.resolve(eq(recorded)))
        .thenReturn(
            new Replay(
                new CreatedSaleResponse(obligation.getValue().saleId(), 201, response.getBody())));
    assertThat(controller.create(headers("invoice-1"), request()).getBody())
        .isEqualTo(response.getBody());
  }

  @Test
  void rejectsMissingOrMalformedKeysBeforeResolvingTheClaim() {
    assertThatThrownBy(() -> controller.create(new HttpHeaders(), request()))
        .isInstanceOf(InvalidIdempotencyKey.class);
    assertThatThrownBy(() -> controller.create(headers("invalid key"), request()))
        .isInstanceOf(InvalidIdempotencyKey.class);
    verifyNoInteractions(keys, sales, invoices, catalog);
  }

  private CreateInvoiceController controller() {
    var clock = Clock.fixed(Instant.parse("2026-11-23T20:00:00Z"), ZoneOffset.UTC);
    var authorization = mock(SalesAuthorization.class);
    var register = new CreateSale(authorization, catalog, sales, clock, (item, tenant) -> false);
    return new CreateInvoiceController(
        new CreateSaleIdempotently(authorization, () -> actor, register, keys),
        new IssueInvoice(
            () -> new CompanyContext(company, "Empresa"), () -> actor, invoices, clock),
        json);
  }

  private CreateInvoiceRequest request() {
    return new CreateInvoiceRequest(
        client,
        List.of(
            new Line(
                item,
                new BigDecimal("2"),
                new BigDecimal("10.1234"),
                BigDecimal.ONE,
                new BigDecimal("0.18"))),
        LocalDate.of(2026, 11, 23),
        SaleMode.CASH,
        null,
        RequestedReceiptType.FISCAL_CREDIT,
        "Entrega");
  }

  private static HttpHeaders headers(String key) {
    var headers = new HttpHeaders();
    headers.set("Idempotency-Key", key);
    return headers;
  }
}
