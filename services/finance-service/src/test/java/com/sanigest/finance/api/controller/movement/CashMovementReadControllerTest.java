package com.sanigest.finance.api.controller.movement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.companyaccess.application.port.out.CompanyAccess.Scope;
import com.sanigest.finance.api.controller.movement.CashMovementReadController.Filters;
import com.sanigest.finance.api.security.CashAccess;
import com.sanigest.finance.application.dto.movement.ManualMovement.Method;
import com.sanigest.finance.application.dto.movement.ManualMovement.Type;
import com.sanigest.finance.application.port.in.movement.CashMovementQueries;
import com.sanigest.finance.application.port.in.movement.CashMovementQueries.Criteria;
import com.sanigest.finance.application.port.in.movement.CashMovementQueries.Origin;
import com.sanigest.finance.application.port.in.movement.CashMovementQueries.Page;
import com.sanigest.finance.application.port.in.movement.CashMovementQueries.Row;
import com.sanigest.finance.application.port.in.movement.CashMovementQueries.Summary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class CashMovementReadControllerTest {
  private final UUID company = UUID.randomUUID();
  private final CashAccess access = mock(CashAccess.class);
  private final CashMovementQueries movements = mock(CashMovementQueries.class);
  private final CashMovementReadController controller =
      new CashMovementReadController(movements, access);
  private final MockHttpServletRequest request = new MockHttpServletRequest();

  CashMovementReadControllerTest() {
    request.addHeader("X-Company-Id", company.toString());
  }

  @Test
  void sharesCompanyAndNormalizedCriteriaAcrossReadRoutes() {
    when(access.require(List.of(company.toString()), Scope.READ)).thenReturn(company);
    var date = LocalDate.of(2026, 11, 28);
    var filters = new Filters(" Ref ", "INCOME", "RECEIVABLE_PAYMENT", "CARD", "SALES", date, date);
    var criteria =
        new Criteria(
            "Ref", Type.INCOME, Origin.RECEIVABLE_PAYMENT, Method.CARD, "SALES", date, date);
    List<Row> rows = List.of();
    var page = new Page(rows, 2, 10, 0, 0);
    var zero = BigDecimal.ZERO;
    var summary = new Summary(zero, zero, zero, zero, zero, 0);
    when(movements.page(company, criteria, 2, 10)).thenReturn(page);
    when(movements.all(company, criteria)).thenReturn(rows);
    when(movements.summary(company, criteria)).thenReturn(summary);

    assertThat(controller.page(request, filters, 2, 10)).isSameAs(page);
    assertThat(controller.all(request, filters)).isSameAs(rows);
    assertThat(controller.summary(request, filters)).isSameAs(summary);
  }

  @Test
  void allRejectsOnlyResultsOverTheCap() {
    when(access.require(List.of(company.toString()), Scope.READ)).thenReturn(company);
    var filters = new Filters(null, "ALL", "", null, "ALL", null, null);
    var criteria = new Criteria("", null, null, null, null, null, null);
    var row = mock(Row.class);
    var limit = Collections.nCopies(CashMovementQueries.ALL_LIMIT, row);
    var overflow = Collections.nCopies(CashMovementQueries.ALL_LIMIT + 1, row);
    when(movements.all(company, criteria)).thenReturn(limit).thenReturn(overflow);

    assertThat(controller.all(request, filters)).hasSize(CashMovementQueries.ALL_LIMIT);
    assertThat(
            assertThrows(ResponseStatusException.class, () -> controller.all(request, filters))
                .getStatusCode()
                .value())
        .isEqualTo(422);
  }

  @Test
  void rejectsInvalidFiltersBeforeQuerying() {
    when(access.require(List.of(company.toString()), Scope.READ)).thenReturn(company);
    var day = LocalDate.of(2026, 11, 28);
    var invalid =
        List.of(
            new Filters("", null, "UNKNOWN", null, null, null, null),
            new Filters("", null, null, null, "UNKNOWN", null, null),
            new Filters("", null, null, null, null, day, day.minusDays(1)),
            new Filters("", null, null, null, null, LocalDate.of(0, 1, 1), null),
            new Filters("bad\0search", null, null, null, null, null, null));
    for (var filters : invalid) {
      assertThat(
              assertThrows(ResponseStatusException.class, () -> controller.all(request, filters))
                  .getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    verifyNoInteractions(movements);
  }

  @Test
  void deniesReadsBeforeQueryingWhenCompanyAccessFails() {
    when(access.require(List.of(company.toString()), Scope.READ))
        .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
    var filters = new Filters(null, null, null, null, "OTHER_INCOME", null, null);

    assertThat(
            assertThrows(ResponseStatusException.class, () -> controller.summary(request, filters))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    verifyNoInteractions(movements);
    assertThat(filters.criteria().category()).isEqualTo("OTHER_INCOME");
  }
}
