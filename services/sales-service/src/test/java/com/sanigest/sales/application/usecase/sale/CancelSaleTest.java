package com.sanigest.sales.application.usecase.sale;

import static com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission.SALES_CANCEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.sales.application.exception.InvalidSaleRequest;
import com.sanigest.sales.application.exception.SaleCancellationConflict;
import com.sanigest.sales.application.exception.SaleNotFound;
import com.sanigest.sales.application.exception.SalesAccessForbidden;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.SaleRepository;
import com.sanigest.sales.domain.model.Sale;
import com.sanigest.sales.domain.model.SaleCancellation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CancelSaleTest {
  private final SalesAuthorization authorization = mock(SalesAuthorization.class);
  private final SaleRepository sales = mock(SaleRepository.class);
  private final UUID company = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID id = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-11-23T14:00:00Z");
  private final CancelSale useCase =
      new CancelSale(authorization, () -> actor, sales, Clock.fixed(now, ZoneOffset.UTC));

  @Test
  void usesAuthorizedCompanyAuthenticatedActorAndClock() {
    when(authorization.requireMembership(SALES_CANCEL)).thenReturn(company);
    var expected = new SaleCancellation(actor, now, "Duplicada");
    var cancelled = mock(Sale.class);
    when(sales.cancel(company, id, expected)).thenReturn(Optional.of(cancelled));

    assertThat(useCase.execute(id, "\u2003Duplicada\t")).isSameAs(cancelled);
    verify(authorization).requireMembership(SALES_CANCEL);
    verify(sales).cancel(company, id, expected);
    verifyNoMoreInteractions(sales);
  }

  @Test
  void returnsTheOriginalCancellationOnReplay() {
    when(authorization.requireMembership(SALES_CANCEL)).thenReturn(company);
    var original = mock(Sale.class);
    var originalMetadata =
        new SaleCancellation(UUID.randomUUID(), now.minusSeconds(10), "Original");
    when(original.cancellation()).thenReturn(originalMetadata);
    when(sales.cancel(company, id, new SaleCancellation(actor, now, "Distinta")))
        .thenReturn(Optional.of(original));

    assertThat(useCase.execute(id, "Distinta").cancellation()).isEqualTo(originalMetadata);
  }

  @Test
  void rejectsInvalidReasonsBeforeWriting() {
    when(authorization.requireMembership(SALES_CANCEL)).thenReturn(company);
    for (String reason : new String[] {null, "", "\u2003\t", "x".repeat(501), "a\u0000b"}) {
      assertThatThrownBy(() -> useCase.execute(id, reason)).isInstanceOf(InvalidSaleRequest.class);
    }
    verifyNoInteractions(sales);
  }

  @Test
  void treatsForeignAndMissingSalesTheSame() {
    when(authorization.requireMembership(SALES_CANCEL)).thenReturn(company);
    UUID foreign = UUID.randomUUID();
    var details = new SaleCancellation(actor, now, "Duplicada");
    assertThatThrownBy(() -> useCase.execute(id, "Duplicada")).isInstanceOf(SaleNotFound.class);
    assertThatThrownBy(() -> useCase.execute(foreign, "Duplicada"))
        .isInstanceOf(SaleNotFound.class);
    verify(sales).cancel(company, id, details);
    verify(sales).cancel(company, foreign, details);
    verifyNoMoreInteractions(sales);
  }

  @Test
  void doesNotWriteWithoutPermission() {
    when(authorization.requireMembership(SALES_CANCEL)).thenThrow(new SalesAccessForbidden());
    assertThatThrownBy(() -> useCase.execute(id, "Duplicada"))
        .isInstanceOf(SalesAccessForbidden.class);
    verifyNoInteractions(sales);
  }

  @Test
  void propagatesAnUpdateConflict() {
    when(authorization.requireMembership(SALES_CANCEL)).thenReturn(company);
    when(sales.cancel(company, id, new SaleCancellation(actor, now, "Duplicada")))
        .thenThrow(new SaleCancellationConflict());
    assertThatThrownBy(() -> useCase.execute(id, "Duplicada"))
        .isInstanceOf(SaleCancellationConflict.class);
  }
}
