package com.sanigest.sales.api.controller;

import static com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission.SALES_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.sales.application.dto.ReceivableDetail;
import com.sanigest.sales.application.dto.ReceivablePage;
import com.sanigest.sales.application.dto.ReceivableSearch;
import com.sanigest.sales.application.dto.ReceivableSearch.Filter;
import com.sanigest.sales.application.exception.InvalidSaleQuery;
import com.sanigest.sales.application.exception.SalesAccessForbidden;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.ReceivableRepository;
import com.sanigest.sales.application.usecase.receivable.GetReceivable;
import com.sanigest.sales.application.usecase.receivable.ListReceivables;
import com.sanigest.sales.domain.enums.ReceivableOrigin;
import com.sanigest.sales.domain.enums.ReceivableStatus;
import com.sanigest.sales.domain.model.Receivable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceivableControllerTest {
  private final SalesAuthorization authorization = mock(SalesAuthorization.class);
  private final ReceivableRepository repository = mock(ReceivableRepository.class);
  private final ReceivableController controller =
      new ReceivableController(
          new GetReceivable(authorization, repository),
          new ListReceivables(authorization, repository, 100));
  private final UUID company = UUID.randomUUID();

  @Test
  void mapsPageAndPrefersInvoiceToSaleAsTheDocument() {
    var account = account();
    var invoice = UUID.randomUUID();
    var detail = new ReceivableDetail(account, "V-1", invoice, "F-1");
    var criteria = new ReceivableSearch("F-1", Filter.PARTIALLY_PAID);
    when(authorization.requireMembership(SALES_READ)).thenReturn(company);
    when(repository.page(company, criteria, 1, 20))
        .thenReturn(new ReceivablePage(List.of(detail), 1, 20, 21));

    var page = controller.page(" F-1 ", Filter.PARTIALLY_PAID, 1, 20);

    assertThat(page.totalPages()).isEqualTo(2);
    assertThat(page.content())
        .singleElement()
        .satisfies(row -> {
          assertThat(row.documentId()).isEqualTo(invoice);
          assertThat(row.documentNumber()).isEqualTo("F-1");
          assertThat(row.balance()).isEqualTo("25.00");
          assertThat(row.status()).isEqualTo(ReceivableStatus.PARTIALLY_PAID);
        });
  }

  @Test
  void usesSaleDocumentWhenThereIsNoInvoice() {
    var account = account();
    var criteria = new ReceivableSearch("V-1", Filter.ALL);
    when(authorization.requireMembership(SALES_READ)).thenReturn(company);
    when(repository.all(company, criteria))
        .thenReturn(List.of(new ReceivableDetail(account, "V-1", null, null)));

    assertThat(controller.all(" V-1 ", Filter.ALL))
        .singleElement()
        .satisfies(row -> {
          assertThat(row.documentId()).isEqualTo(account.saleId());
          assertThat(row.documentNumber()).isEqualTo("V-1");
          assertThat(row.client().name()).isEqualTo("Client");
          assertThat(row.totalAmount()).isEqualTo("100.00");
        });
  }

  @Test
  void rejectsInvalidQueriesAndUnauthorizedReadsBeforeAccessingStorage() {
    when(authorization.requireMembership(SALES_READ)).thenReturn(company);
    assertThatThrownBy(() -> controller.page("", Filter.ALL, -1, 20))
        .isInstanceOf(InvalidSaleQuery.class);
    assertThatThrownBy(() -> controller.page("", Filter.ALL, 0, 101))
        .isInstanceOf(InvalidSaleQuery.class);
    assertThatThrownBy(() -> controller.all("a".repeat(251), Filter.ALL))
        .isInstanceOf(InvalidSaleQuery.class);
    when(authorization.requireMembership(SALES_READ)).thenThrow(new SalesAccessForbidden());
    assertThatThrownBy(() -> controller.all("", Filter.ALL))
        .isInstanceOf(SalesAccessForbidden.class);
    verifyNoInteractions(repository);
  }

  private Receivable account() {
    var saleId = UUID.randomUUID();
    return new Receivable(
        saleId,
        company,
        saleId,
        UUID.randomUUID(),
        "Client",
        new BigDecimal("100"),
        new BigDecimal("25"),
        LocalDate.of(2026, 12, 23),
        ReceivableStatus.PARTIALLY_PAID,
        Instant.EPOCH,
        ReceivableOrigin.SALE);
  }
}
