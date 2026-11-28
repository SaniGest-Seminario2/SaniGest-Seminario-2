package com.sanigest.sales.application.usecase.sale;

import static com.sanigest.sales.application.port.out.integration.company.SalesAuthorization.Permission.SALES_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.sales.application.dto.sale.SalePage;
import com.sanigest.sales.application.dto.sale.SaleSearch;
import com.sanigest.sales.application.dto.sale.SaleTotals;
import com.sanigest.sales.application.exception.sale.InvalidSaleQuery;
import com.sanigest.sales.application.exception.sale.SaleListLimitExceeded;
import com.sanigest.sales.application.exception.security.SalesAccessForbidden;
import com.sanigest.sales.application.port.out.integration.company.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.sale.SaleQueryRepository;
import com.sanigest.sales.domain.enums.sale.SaleMode;
import com.sanigest.sales.domain.enums.sale.SaleStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListSalesTest {
  private static final LocalDate DATE = LocalDate.of(2026, 11, 23);
  private final UUID companyId = UUID.randomUUID();
  @Mock private SalesAuthorization authorization;
  @Mock private SaleQueryRepository repository;
  private ListSales list;

  @BeforeEach
  void configure() {
    list = new ListSales(authorization, repository, 100);
  }

  @Test
  void authorizesReadAndReturnsAnEmptyUnfilteredPage() {
    when(authorization.requireMembership(SALES_READ)).thenReturn(companyId);
    var filters = new SaleSearch("", null, SaleSearch.MIN_DATE, SaleSearch.MAX_DATE, 0, 20);
    var empty = new SalePage(List.of(), 0, 20, 0);
    when(repository.search(companyId, filters)).thenReturn(empty);

    assertThat(list.execute("  ", null, null, null, 0, 20)).isEqualTo(empty);
    assertThat(empty.totalPages()).isZero();
    verify(authorization).requireMembership(SALES_READ);
  }

  @Test
  void stripsSearchAndConvertsTheStateWhilePreservingTheRange() {
    when(authorization.requireMembership(SALES_READ)).thenReturn(companyId);
    list.execute("  aCmE %_!  ", " cancelled ", DATE, DATE, 2, 50);
    var criteria = new SaleSearch("aCmE %_!", SaleStatus.CANCELLED, DATE, DATE, 2, 50);
    verify(repository).search(companyId, criteria);
    assertThat(criteria.offset()).isEqualTo(100);
  }

  @Test
  void fillsOnlyTheMissingDateBound() {
    when(authorization.requireMembership(SALES_READ)).thenReturn(companyId);
    list.execute(null, null, DATE, null, 0, 20);
    list.execute(null, null, null, DATE, 0, 20);
    verify(repository)
        .search(companyId, new SaleSearch("", null, DATE, SaleSearch.MAX_DATE, 0, 20));
    verify(repository)
        .search(companyId, new SaleSearch("", null, SaleSearch.MIN_DATE, DATE, 0, 20));
  }

  @Test
  void rejectsUnsupportedStateInvalidTextAndDates() {
    when(authorization.requireMembership(SALES_READ)).thenReturn(companyId);
    assertThatThrownBy(() -> list.execute(null, "PAID", null, null, 0, 20))
        .isInstanceOf(InvalidSaleQuery.class);
    assertThatThrownBy(() -> list.execute("a".repeat(251), null, null, null, 0, 20))
        .isInstanceOf(InvalidSaleQuery.class);
    assertThatThrownBy(() -> list.execute("a\0", null, null, null, 0, 20))
        .isInstanceOf(InvalidSaleQuery.class);
    assertThatThrownBy(() -> list.execute(null, null, DATE, DATE.minusDays(1), 0, 20))
        .isInstanceOf(InvalidSaleQuery.class);
    assertThatThrownBy(
            () -> list.execute(null, null, SaleSearch.MIN_DATE.minusDays(1), null, 0, 20))
        .isInstanceOf(InvalidSaleQuery.class);
    verifyNoInteractions(repository);
  }

  @ParameterizedTest
  @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "2147483647,20"})
  void rejectsInvalidPagination(int page, int size) {
    when(authorization.requireMembership(SALES_READ)).thenReturn(companyId);
    assertThatThrownBy(() -> list.execute(null, null, null, null, page, size))
        .isInstanceOf(InvalidSaleQuery.class);
    verifyNoInteractions(repository);
  }

  @Test
  void boundsTheUnpagedListAndUsesTheSameFiltersForTotals() {
    when(authorization.requireMembership(SALES_READ)).thenReturn(companyId);
    int limit = ListSales.MAX_UNPAGED_RESULTS;
    var filter =
        new SaleSearch("Factura", SaleStatus.REGISTERED, DATE, DATE, 0, limit, SaleMode.CREDIT);
    when(repository.search(companyId, filter))
        .thenReturn(new SalePage(List.of(), 0, limit, 0))
        .thenReturn(new SalePage(List.of(), 0, limit, limit + 1));
    assertThat(list.all(" Factura ", "registered", "credit", DATE, DATE)).isEmpty();
    assertThatThrownBy(() -> list.all("Factura", "registered", "credit", DATE, DATE))
        .isInstanceOf(SaleListLimitExceeded.class);

    var zero = new BigDecimal("0.00");
    var total = new BigDecimal("10.01");
    var totals = new SaleTotals(total, zero, total, 2, zero);
    when(repository.totals(
            companyId,
            new SaleSearch("Factura", SaleStatus.REGISTERED, DATE, DATE, 0, 1, SaleMode.CREDIT)))
        .thenReturn(totals);
    assertThat(list.summary("Factura", "registered", "credit", DATE, DATE).averageTicket())
        .isEqualByComparingTo("5.01");
    assertThat(new SaleTotals(zero, zero, zero, 0, zero).averageTicket()).isNull();
  }

  @Test
  void neverQueriesWithoutAnAuthorizedCompany() {
    when(authorization.requireMembership(SALES_READ)).thenThrow(new SalesAccessForbidden());
    assertThatThrownBy(() -> list.execute(null, null, null, null, 0, 20))
        .isInstanceOf(SalesAccessForbidden.class);
    verifyNoInteractions(repository);
  }
}
