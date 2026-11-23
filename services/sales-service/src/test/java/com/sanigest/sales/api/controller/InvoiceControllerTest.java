package com.sanigest.sales.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.sales.application.exception.InvalidSaleRequest;
import com.sanigest.sales.application.port.out.integration.InvoiceCompanyAccess.CompanyContext;
import com.sanigest.sales.application.port.out.persistence.InvoiceRepository;
import com.sanigest.sales.application.usecase.invoice.IssueInvoice;
import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.model.CatalogCustomerSnapshot;
import com.sanigest.sales.domain.model.InternalInvoice;
import com.sanigest.sales.domain.model.Sale;
import com.sanigest.sales.domain.model.SaleLine;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

class InvoiceControllerTest {
  private final UUID company = UUID.randomUUID();
  private final UUID saleId = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final InvoiceRepository repository = mock(InvoiceRepository.class);
  private final ObjectMapper json = new ObjectMapper();
  private final InvoiceController controller =
      new InvoiceController(
          new IssueInvoice(
              () -> new CompanyContext(company, "Empresa"),
              () -> actor,
              repository,
              Clock.fixed(Instant.parse("2026-11-23T20:00:00Z"), ZoneOffset.UTC)),
          json);

  @Test
  void serializesHistoricalInvoiceAndReturnsCreated() throws Exception {
    var customer =
        new CatalogCustomerSnapshot(
            UUID.randomUUID(), "Cliente", CatalogCustomerSnapshot.Status.ACTIVE);
    var line =
        new SaleLine(
            UUID.randomUUID(),
            saleId,
            UUID.randomUUID(),
            "Servicio",
            "Histórico",
            new BigDecimal("15.00"),
            BigDecimal.ONE,
            new BigDecimal("15.00"));
    var sale =
        Sale.register(
            saleId, company, customer, LocalDate.of(2026, 11, 20), SaleMode.CASH, List.of(line));
    when(repository.issue(any(), any(), any()))
        .thenAnswer(invocation -> {
          BiFunction<Sale, Supplier<String>, InternalInvoice> create = invocation.getArgument(1);
          Function<InternalInvoice, String> render = invocation.getArgument(2);
          return render.apply(create.apply(sale, () -> "FAC-2026-001"));
        });

    var response = controller.issue(saleId.toString(), key(), InputStream.nullInputStream());
    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    var body = json.readTree(response.getBody());
    assertThat(body.path("number").asString()).isEqualTo("FAC-2026-001");
    assertThat(body.path("company").path("name").asString()).isEqualTo("Empresa");
    assertThat(body.path("client").path("name").asString()).isEqualTo("Cliente");
    assertThat(body.path("lines").get(0).path("itemDescription").asString()).isEqualTo("Histórico");
    assertThat(body.path("total").asString()).isEqualTo("15.00");
    assertThat(body.path("fiscalClassification").asString()).isEqualTo("NOT_FISCAL");
  }

  @Test
  void rejectsInvalidKeysUuidAndNonemptyBodyBeforePersistence() {
    var repeated = key();
    repeated.add("Idempotency-Key", "another");
    for (var headers : List.of(new HttpHeaders(), repeated)) {
      assertThatThrownBy(
              () -> controller.issue(saleId.toString(), headers, InputStream.nullInputStream()))
          .isInstanceOf(InvalidIdempotencyKey.class);
    }
    for (String invalid : List.of("not-a-uuid", "1-1-1-1-1")) {
      assertThatThrownBy(() -> controller.issue(invalid, key(), InputStream.nullInputStream()))
          .isInstanceOf(InvalidSaleRequest.class);
    }
    assertThatThrownBy(() ->
            controller.issue(saleId.toString(), key(), new ByteArrayInputStream(new byte[] {'{'})))
        .isInstanceOf(InvalidSaleRequest.class);
    verifyNoInteractions(repository);
  }

  private static HttpHeaders key() {
    var headers = new HttpHeaders();
    headers.set("Idempotency-Key", "invoice-1");
    return headers;
  }
}
