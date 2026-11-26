package com.sanigest.sales.application.usecase.sale;

import static com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission.SALES_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.sales.application.dto.SaleDetail;
import com.sanigest.sales.application.dto.SaleFinancials;
import com.sanigest.sales.application.exception.SaleNotFound;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.SaleQueryRepository;
import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.enums.SaleStatus;
import com.sanigest.sales.domain.model.CatalogCustomerSnapshot;
import com.sanigest.sales.domain.model.Sale;
import com.sanigest.sales.domain.model.SaleCancellation;
import com.sanigest.sales.domain.model.SaleLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetSaleDetailTest {
  private final SalesAuthorization authorization = mock(SalesAuthorization.class);
  private final SaleQueryRepository sales = mock(SaleQueryRepository.class);
  private final GetSaleDetail useCase = new GetSaleDetail(authorization, sales);
  private final UUID company = UUID.randomUUID();
  private final UUID id = UUID.randomUUID();

  @Test
  void returnsHistoricalDetailsInStoredOrderWithoutWriting() {
    var first = line("Original", "12.50");
    var second = line("Servicio", "0.01");
    var client =
        new CatalogCustomerSnapshot(
            UUID.randomUUID(), "Cliente original", CatalogCustomerSnapshot.Status.ACTIVE);
    var cancellation = new SaleCancellation(UUID.randomUUID(), Instant.EPOCH, "Duplicada");
    Sale historical =
        Sale.register(
                id,
                company,
                client,
                LocalDate.of(2026, 11, 23),
                SaleMode.CREDIT,
                List.of(first, second))
            .withCode("V-2026-001")
            .cancel(cancellation);
    when(authorization.requireMembership(SALES_READ)).thenReturn(company);
    when(sales.detail(company, id))
        .thenReturn(
            Optional.of(
                new SaleDetail(
                    historical, new SaleFinancials(null, null, null, null, null, null))));
    Sale result = useCase.execute(id).sale();

    assertThat(result.clientId()).isEqualTo(client.id());
    assertThat(result.clientName()).isEqualTo("Cliente original");
    assertThat(result.lines()).containsExactly(first, second);
    assertThat(result.lines().getFirst().itemDescription()).isEqualTo("Descripción histórica");
    assertThat(result.total()).isEqualTo(new BigDecimal("12.51"));
    assertThat(result.status()).isEqualTo(SaleStatus.CANCELLED);
    assertThat(result.cancellation()).isEqualTo(cancellation);
    verify(authorization).requireMembership(SALES_READ);
    verify(sales).detail(company, id);
    verifyNoMoreInteractions(sales);
  }

  @Test
  void hidesMissingAndForeignSales() {
    UUID foreign = UUID.randomUUID();
    when(authorization.requireMembership(SALES_READ)).thenReturn(company);

    assertThatThrownBy(() -> useCase.execute(id)).isInstanceOf(SaleNotFound.class);
    assertThatThrownBy(() -> useCase.execute(foreign)).isInstanceOf(SaleNotFound.class);
    verify(sales).detail(company, id);
    verify(sales).detail(company, foreign);
    verifyNoMoreInteractions(sales);
  }

  private SaleLine line(String name, String price) {
    var amount = new BigDecimal(price);
    return new SaleLine(
        UUID.randomUUID(),
        id,
        UUID.randomUUID(),
        name,
        "Descripción histórica",
        amount,
        BigDecimal.ONE,
        amount);
  }
}
