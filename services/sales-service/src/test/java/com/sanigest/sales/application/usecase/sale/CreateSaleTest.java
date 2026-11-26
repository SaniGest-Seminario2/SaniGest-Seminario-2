package com.sanigest.sales.application.usecase.sale;

import static com.sanigest.sales.application.exception.CatalogResourceInvalidException.Reason.NOT_FOUND_OR_NOT_VISIBLE;
import static com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission.SALES_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.sales.application.dto.CreateSaleCommand;
import com.sanigest.sales.application.dto.CreateSaleCommand.Line;
import com.sanigest.sales.application.exception.CatalogResourceInvalidException;
import com.sanigest.sales.application.exception.CatalogUnavailableException;
import com.sanigest.sales.application.exception.InvalidSaleRequest;
import com.sanigest.sales.application.exception.SaleAmountNotRepresentable;
import com.sanigest.sales.application.exception.SalesAccessForbidden;
import com.sanigest.sales.application.port.out.integration.CatalogClient;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.SaleRepository;
import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.enums.SaleStatus;
import com.sanigest.sales.domain.model.CatalogCustomerSnapshot;
import com.sanigest.sales.domain.model.CatalogCustomerSnapshot.Status;
import com.sanigest.sales.domain.model.CatalogItemSnapshot;
import com.sanigest.sales.domain.model.CatalogItemSnapshot.Type;
import com.sanigest.sales.domain.model.Sale;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CreateSaleTest {
  private final UUID tenant = UUID.randomUUID();
  private final UUID client = UUID.randomUUID();
  private final UUID product = UUID.randomUUID();
  private final UUID service = UUID.randomUUID();
  private final UUID free = UUID.randomUUID();
  private final SalesAuthorization authorization = mock(SalesAuthorization.class);
  private final CatalogClient catalog = mock(CatalogClient.class);
  private final SaleRepository repository = mock(SaleRepository.class);
  private final CreateSale create =
      new CreateSale(
          authorization,
          catalog,
          repository,
          Clock.fixed(Instant.parse("2026-11-24T02:00:00Z"), ZoneId.of("America/Santo_Domingo")),
          (item, company) -> false);

  @BeforeEach
  void authorize() {
    when(authorization.requireMembership(SALES_WRITE)).thenReturn(tenant);
  }

  @ParameterizedTest
  @CsvSource({"CASH,2026-11-23,1640.10", "CREDIT,2025-01-01,1640.10"})
  void computesExactAmountsAndKeepsOrderedSnapshots(SaleMode mode, LocalDate date, String total) {
    activeClient();
    when(catalog.getItem(product, tenant)).thenReturn(item(product, Type.PRODUCT, "250.00", true));
    when(catalog.getItem(service, tenant)).thenReturn(item(service, Type.SERVICE, "3200.50", true));
    when(catalog.getItem(free, tenant)).thenReturn(item(free, Type.SERVICE, "0", true));
    if (mode == SaleMode.CREDIT) {
      when(repository.register(any(), any()))
          .thenAnswer(invocation -> {
            var obligation = invocation.<com.sanigest.sales.domain.model.Receivable>getArgument(1);
            assertThat(obligation.totalAmount()).isEqualTo(new BigDecimal(total));
            assertThat(obligation.balance()).isEqualTo(obligation.totalAmount());
            assertThat(obligation.companyId()).isEqualTo(tenant);
            assertThat(obligation.clientId()).isEqualTo(client);
            return invocation.<Sale>getArgument(0).withCode("V-%s-001".formatted(date.getYear()));
          });
    } else {
      when(repository.register(any()))
          .thenAnswer(invocation ->
              invocation.<Sale>getArgument(0).withCode("V-%s-001".formatted(date.getYear())));
    }
    var lines = List.of(line(product, "4"), line(service, "0.2"));
    if (mode == SaleMode.CREDIT) {
      lines = List.of(line(product, "4"), line(service, "0.2"), line(free, "0.001"));
    }
    var result =
        create.execute(
            new CreateSaleCommand(
                client,
                lines,
                mode == SaleMode.CASH ? null : date,
                mode,
                mode == SaleMode.CREDIT ? date.plusDays(1) : null));

    assertThat(result.id()).isNotNull();
    assertThat(result.companyId()).isEqualTo(tenant);
    assertThat(result.code()).isEqualTo("V-%s-001".formatted(date.getYear()));
    assertThat(result.clientId()).isEqualTo(client);
    assertThat(result.clientName()).isEqualTo("Cliente original");
    assertThat(result.mode()).isEqualTo(mode);
    assertThat(result.status()).isEqualTo(SaleStatus.REGISTERED);
    assertThat(result.saleDate()).isEqualTo(date);
    assertThat(result.total()).isEqualTo(new BigDecimal(total));
    assertThat(result.lines())
        .hasSize(lines.size())
        .allSatisfy(line -> {
          assertThat(line.id()).isNotNull();
          assertThat(line.saleId()).isEqualTo(result.id());
        });
    assertThat(result.lines().getFirst().catalogItemId()).isEqualTo(product);
    assertThat(result.lines().get(1).catalogItemId()).isEqualTo(service);
    assertThat(result.lines().get(1).itemDescription()).isEqualTo("Descripción");
    when(catalog.getItem(product, tenant)).thenReturn(item(product, Type.PRODUCT, "999", true));
    assertThat(result.lines().getFirst().unitPrice()).isEqualTo(new BigDecimal("250.00"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"0", "-1", "0.0001", "1000000000000"})
  void rejectsBadQuantityBeforeCatalog(String raw) {
    var line = new Line(product, raw == null || raw.isEmpty() ? null : new BigDecimal(raw));
    assertThatThrownBy(() -> create.execute(command(List.of(line))))
        .isInstanceOf(InvalidSaleRequest.class);
    verifyNoInteractions(catalog, repository);
  }

  @Test
  void rejectsEmptyAndExcessiveLines() {
    for (var lines : List.of(List.<Line>of(), Collections.nCopies(101, line(product, "1")))) {
      assertThatThrownBy(() -> create.execute(command(lines)))
          .isInstanceOf(InvalidSaleRequest.class);
    }
    verifyNoInteractions(catalog, repository);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void rejectsUnavailableOrInactiveCustomer(boolean inactive) {
    if (inactive) {
      when(catalog.getCustomer(client, tenant))
          .thenReturn(new CatalogCustomerSnapshot(client, "Cliente", Status.INACTIVE));
    } else {
      when(catalog.getCustomer(client, tenant))
          .thenThrow(new CatalogResourceInvalidException(NOT_FOUND_OR_NOT_VISIBLE));
    }
    assertThatThrownBy(() -> create.execute(command(List.of(line(product, "1")))))
        .isInstanceOf(CatalogResourceInvalidException.class);
    verifyNoInteractions(repository);
  }

  @ParameterizedTest
  @CsvSource({"0.01,0.001", "999999999999.00,999999999999"})
  void rejectsInexactSubtotalsAndOverflow(String price, String quantity) {
    activeClient();
    when(catalog.getItem(product, tenant)).thenReturn(item(product, Type.PRODUCT, price, true));
    assertThatThrownBy(() -> create.execute(command(List.of(line(product, quantity)))))
        .isInstanceOf(SaleAmountNotRepresentable.class);
    verifyNoInteractions(repository);
  }

  @Test
  void rejectsAnIneligibleOrUnavailableSecondItemWithoutInsertingTheFirst() {
    activeClient();
    when(catalog.getItem(product, tenant)).thenReturn(item(product, Type.PRODUCT, "5", true));
    when(catalog.getItem(service, tenant)).thenReturn(item(service, Type.SERVICE, "5", false));
    var command = command(List.of(line(product, "2"), line(service, "1")));
    assertThatThrownBy(() -> create.execute(command))
        .isInstanceOf(CatalogResourceInvalidException.class);
    when(catalog.getItem(service, tenant))
        .thenThrow(
            new CatalogUnavailableException(CatalogUnavailableException.Reason.DEPENDENCY_FAILURE));
    assertThatThrownBy(() -> create.execute(command))
        .isInstanceOf(CatalogUnavailableException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void doesNotContactCatalogOrRepositoryWhenWriteIsDenied() {
    when(authorization.requireMembership(SALES_WRITE)).thenThrow(new SalesAccessForbidden());
    assertThatThrownBy(() -> create.execute(command(List.of(line(product, "1")))))
        .isInstanceOf(SalesAccessForbidden.class);
    verifyNoInteractions(catalog, repository);
  }

  private void activeClient() {
    when(catalog.getCustomer(client, tenant))
        .thenReturn(new CatalogCustomerSnapshot(client, "Cliente original", Status.ACTIVE));
  }

  private CreateSaleCommand command(List<Line> lines) {
    return new CreateSaleCommand(client, lines, null, SaleMode.CASH);
  }

  private static Line line(UUID id, String quantity) {
    return new Line(id, new BigDecimal(quantity));
  }

  private static CatalogItemSnapshot item(UUID id, Type type, String price, boolean active) {
    return new CatalogItemSnapshot(
        id, type.name(), type, "Descripción", new BigDecimal(price), "DOP", active);
  }
}
