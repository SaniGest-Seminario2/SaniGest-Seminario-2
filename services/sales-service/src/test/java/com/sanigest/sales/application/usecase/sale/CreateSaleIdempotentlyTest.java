package com.sanigest.sales.application.usecase.sale;

import static com.sanigest.sales.application.port.out.integration.company.SalesAuthorization.Permission.SALES_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.sales.application.dto.sale.CreateSaleCommand;
import com.sanigest.sales.application.dto.sale.CreateSaleCommand.Line;
import com.sanigest.sales.application.dto.sale.CreatedSaleResponse;
import com.sanigest.sales.application.exception.catalog.CatalogUnavailableException;
import com.sanigest.sales.application.exception.idempotency.IdempotencyConflict;
import com.sanigest.sales.application.exception.idempotency.IdempotencyInProgress;
import com.sanigest.sales.application.exception.sale.InvalidSaleRequest;
import com.sanigest.sales.application.exception.security.SalesAccessForbidden;
import com.sanigest.sales.application.port.out.integration.company.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.sale.SaleIdempotencyRepository;
import com.sanigest.sales.application.port.out.persistence.sale.SaleIdempotencyRepository.Claim;
import com.sanigest.sales.application.port.out.persistence.sale.SaleIdempotencyRepository.Replay;
import com.sanigest.sales.application.port.out.persistence.sale.SaleIdempotencyRepository.Request;
import com.sanigest.sales.domain.enums.sale.SaleMode;
import com.sanigest.sales.domain.model.sale.Sale;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateSaleIdempotentlyTest {
  private final SalesAuthorization authorization = mock(SalesAuthorization.class);
  private final CreateSale create = mock(CreateSale.class);
  private final SaleIdempotencyRepository idempotency = mock(SaleIdempotencyRepository.class);
  private final UUID company = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final CreateSaleIdempotently useCase =
      new CreateSaleIdempotently(authorization, () -> actor, create, idempotency);
  private final CreateSaleCommand command =
      new CreateSaleCommand(
          UUID.randomUUID(),
          List.of(new Line(UUID.randomUUID(), BigDecimal.ONE)),
          null,
          SaleMode.CASH);
  private final Claim claim = new Claim(UUID.randomUUID());
  private final CreatedSaleResponse result = new CreatedSaleResponse(UUID.randomUUID(), 201, "{}");

  @BeforeEach
  void grantsMembership() {
    when(authorization.requireMembership(SALES_WRITE)).thenReturn(company);
  }

  @Test
  void claimsThenPreparesAndRegistersThroughTheTransaction() {
    when(idempotency.resolve(any())).thenReturn(claim);
    Sale prepared = mock(Sale.class);
    Sale registered = mock(Sale.class);
    when(create.prepare(command, company)).thenReturn(prepared);
    when(create.register(prepared, null)).thenReturn(registered);
    when(idempotency.complete(any(), eq(claim), any()))
        .thenAnswer(invocation -> invocation.<Supplier<CreatedSaleResponse>>getArgument(2).get());
    assertThat(
            useCase.execute(
                "test",
                command,
                sale -> {
                  assertThat(sale).isSameAs(registered);
                  return result;
                }))
        .isSameAs(result);
    verify(idempotency)
        .resolve(
            new Request(
                company,
                actor,
                "POST /api/v1/sales",
                "test",
                1,
                SaleRequestFingerprint.hash(command)));
  }

  @Test
  void replaysWithoutPreparingOrPersistingAndChecksNewAuthorization() {
    when(idempotency.resolve(any())).thenReturn(new Replay(result));
    assertThat(useCase.execute("test", command, unexpectedResponse())).isSameAs(result);
    when(authorization.requireMembership(SALES_WRITE)).thenThrow(new SalesAccessForbidden());
    assertThatThrownBy(() -> useCase.execute("test", command, unexpectedResponse()))
        .isInstanceOf(SalesAccessForbidden.class);
    verify(idempotency).resolve(any());
    verifyNoInteractions(create);
  }

  @Test
  void stopsBeforePreparationOnConflictBusyOrInvalidRequest() {
    when(idempotency.resolve(any()))
        .thenThrow(new IdempotencyConflict(), new IdempotencyInProgress());
    assertThatThrownBy(() -> useCase.execute("test", command, unexpectedResponse()))
        .isInstanceOf(IdempotencyConflict.class);
    assertThatThrownBy(() -> useCase.execute("test", command, unexpectedResponse()))
        .isInstanceOf(IdempotencyInProgress.class);
    assertThatThrownBy(() -> useCase.execute("test", null, unexpectedResponse()))
        .isInstanceOf(InvalidSaleRequest.class);
    verifyNoInteractions(create);
  }

  @Test
  void releasesClaimOnRemoteAndPersistenceErrors() {
    when(idempotency.resolve(any())).thenReturn(claim);
    var unavailable =
        new CatalogUnavailableException(CatalogUnavailableException.Reason.DEPENDENCY_FAILURE);
    when(create.prepare(command, company)).thenThrow(unavailable).thenReturn(mock(Sale.class));
    assertThatThrownBy(() -> useCase.execute("test", command, unexpectedResponse()))
        .isSameAs(unavailable);
    var databaseFailure = new IllegalStateException("database write failed");
    when(idempotency.complete(any(), eq(claim), any())).thenThrow(databaseFailure);
    assertThatThrownBy(() -> useCase.execute("test", command, unexpectedResponse()))
        .isSameAs(databaseFailure);
    verify(idempotency, org.mockito.Mockito.times(2)).release(any(), eq(claim));
    org.mockito.Mockito.verify(create, org.mockito.Mockito.never()).register(any(), any());
  }

  @Test
  void fingerprintsBusinessFieldsInOrderAndNormalizesExactDecimals() {
    var first = command.lines().getFirst();
    var second = new Line(UUID.randomUUID(), new BigDecimal("2"));
    var lines = List.of(first, second);
    String hash = fingerprint(command.clientId(), lines, null, SaleMode.CASH);
    assertThat(hash)
        .hasSize(64)
        .isEqualTo(
            fingerprint(
                command.clientId(),
                List.of(new Line(first.catalogItemId(), new BigDecimal("1.0000")), second),
                null,
                SaleMode.CASH));
    assertThat(hash)
        .isNotEqualTo(fingerprint(command.clientId(), lines.reversed(), null, SaleMode.CASH))
        .isNotEqualTo(fingerprint(UUID.randomUUID(), lines, null, SaleMode.CASH))
        .isNotEqualTo(
            fingerprint(command.clientId(), lines, LocalDate.of(2026, 11, 23), SaleMode.CASH))
        .isNotEqualTo(fingerprint(command.clientId(), lines, null, SaleMode.CREDIT))
        .isNotEqualTo(
            fingerprint(
                command.clientId(),
                List.of(new Line(first.catalogItemId(), BigDecimal.TEN), second),
                null,
                SaleMode.CASH))
        .isNotEqualTo(
            fingerprint(
                command.clientId(),
                List.of(new Line(UUID.randomUUID(), BigDecimal.ONE), second),
                null,
                SaleMode.CASH));
  }

  private static String fingerprint(UUID client, List<Line> lines, LocalDate date, SaleMode mode) {
    return SaleRequestFingerprint.hash(new CreateSaleCommand(client, lines, date, mode));
  }

  private static Function<Sale, CreatedSaleResponse> unexpectedResponse() {
    return sale -> {
      throw new AssertionError("The replay must never build another response");
    };
  }
}
