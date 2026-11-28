package com.sanigest.sales.api.controller.invoice;

import static com.sanigest.sales.application.port.out.integration.company.SalesAuthorization.Permission.SALES_CANCEL;
import static com.sanigest.sales.application.port.out.integration.company.SalesAuthorization.Permission.SALES_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sanigest.sales.api.dto.sale.CancelSaleRequest;
import com.sanigest.sales.application.dto.invoice.InvoicePage;
import com.sanigest.sales.application.exception.invoice.InvoiceNotFound;
import com.sanigest.sales.application.exception.sale.InvalidSaleQuery;
import com.sanigest.sales.application.exception.sale.InvalidSaleRequest;
import com.sanigest.sales.application.port.out.integration.company.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.invoice.InvoiceDocuments;
import com.sanigest.sales.application.port.out.persistence.invoice.InvoiceDocuments.Search;
import com.sanigest.sales.application.usecase.invoice.ManageInvoices;
import com.sanigest.sales.domain.model.sale.SaleCancellation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

class InvoiceDocumentsControllerTest {
  private static final String DOCUMENT = "{\"number\":\"FAC-2026-001\"}";
  private static final Instant NOW = Instant.parse("2026-11-23T20:00:00Z");
  private final UUID company = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID id = UUID.randomUUID();

  private final SalesAuthorization access = mock(SalesAuthorization.class);
  private final InvoiceDocuments documents = mock(InvoiceDocuments.class);
  private final InvoiceDocumentsController controller =
      new InvoiceDocumentsController(
          new ManageInvoices(access, documents, () -> actor, Clock.fixed(NOW, ZoneOffset.UTC)),
          new ObjectMapper());

  @BeforeEach
  void authorize() {
    when(access.requireMembership(SALES_READ)).thenReturn(company);
    when(access.requireMembership(SALES_CANCEL)).thenReturn(company);
  }

  @Test
  void sharesSearchAndSortingCriteriaAcrossBothCollectionShapes() {
    when(documents.list(company, new Search("%A!%!_%", "ISSUED", true, 2, 2, true)))
        .thenReturn(new InvoicePage(List.of(DOCUMENT), 3));
    var page = controller.page(" A%_ ", " issued ", 1, 2, path("/collectible"));
    assertThat(page.content().getFirst().path("number").asString()).isEqualTo("FAC-2026-001");
    assertThat(page.totalPages()).isEqualTo(2);

    when(documents.list(
            company,
            new Search(
                "%A!%!_%", "ISSUED", true, 0, ManageInvoices.MAX_UNPAGED_RESULTS + 1, false)))
        .thenReturn(new InvoicePage(List.of(DOCUMENT), 1));
    assertThat(controller.all("A%_", "issued", path("/collectible/all"))).isEqualTo(page.content());

    when(documents.list(company, new Search("%%", "", false, 0, 20, true)))
        .thenReturn(new InvoicePage(List.of(), 0));
    assertThat(controller.page(null, null, 0, 20, path("")).totalPages()).isZero();
  }

  @Test
  void validatesScopePaginationAndCancellationReason() {
    assertThatThrownBy(() -> controller.page("a", "UNKNOWN", 0, 20, path("")))
        .isInstanceOf(InvalidSaleQuery.class);
    assertThatThrownBy(() -> controller.page(null, null, Integer.MAX_VALUE, 20, path("")))
        .isInstanceOf(InvalidSaleQuery.class);
    assertThatThrownBy(() -> controller.detail("bad-id")).isInstanceOf(InvalidSaleRequest.class);
    assertThatThrownBy(() -> controller.detail("1-1-1-1-1")).isInstanceOf(InvalidSaleRequest.class);
    assertThatThrownBy(() -> controller.detail(id.toString())).isInstanceOf(InvoiceNotFound.class);
    assertThatThrownBy(() -> controller.cancel(id.toString(), new CancelSaleRequest(" ")))
        .isInstanceOf(InvalidSaleRequest.class);
    assertThatThrownBy(() -> controller.cancel(id.toString(), new CancelSaleRequest("Error")))
        .isInstanceOf(InvoiceNotFound.class);
  }

  @Test
  void readsAndCancelsUsingTheAuthorizedCompany() {
    when(documents.find(company, id)).thenReturn(Optional.of(DOCUMENT));
    when(documents.cancel(company, id, new SaleCancellation(actor, NOW, "Error")))
        .thenReturn(Optional.of("{\"status\":\"CANCELLED\"}"));

    assertThat(controller.detail(id.toString()).path("number").asString())
        .isEqualTo("FAC-2026-001");
    assertThat(
            controller
                .cancel(id.toString(), new CancelSaleRequest(" Error "))
                .path("status")
                .asString())
        .isEqualTo("CANCELLED");
  }

  private static MockHttpServletRequest path(String suffix) {
    var request = new MockHttpServletRequest();
    request.setServletPath("/api/v1/invoices" + suffix);
    return request;
  }
}
