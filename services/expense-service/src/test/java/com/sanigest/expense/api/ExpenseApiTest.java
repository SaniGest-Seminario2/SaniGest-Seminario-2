package com.sanigest.expense.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sanigest.companyaccess.CompanyAccess;
import com.sanigest.companyaccess.CompanyAccess.MembershipContext;
import com.sanigest.companyaccess.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.CompanyAccess.Scope;
import com.sanigest.events.payment.PaymentMethodV1;
import com.sanigest.expense.api.dto.PayablePaymentRequest;
import com.sanigest.expense.application.ExpenseFailure;
import com.sanigest.expense.application.ExpenseQueries;
import com.sanigest.expense.application.ExpenseRegistration.Request;
import com.sanigest.expense.application.ExpenseStore;
import com.sanigest.expense.application.PayableQueries;
import com.sanigest.expense.application.RegisterExpense;
import com.sanigest.expense.application.usecase.RegisterPayablePayment;
import com.sanigest.security.CompanySelector;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

class ExpenseApiTest {
  private final UUID company = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final CompanyAccess memberships = mock();
  private final ExpenseCompanyAccess access =
      new ExpenseCompanyAccess(
          new StaticListableBeanFactory(java.util.Map.of("memberships", memberships))
              .getBeanProvider(CompanyAccess.class));

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void authorizesWritesAndPassesTenantActorAndIdempotencyKey() {
    member(Scope.WRITE, MembershipRole.OWNER, false);
    var jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject(actor.toString())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    var http = request();
    var expenses = mock(RegisterExpense.class);
    var controller = new ExpenseController(expenses, access, new JsonMapper());
    var body = new Request(null, null, null, null, null, null, null, null, null, null, null, null);
    controller.create(http, body);
    verify(expenses)
        .register(
            eq(new ExpenseStore.Key(company, actor, "request-1")),
            matches("[a-f0-9]{64}"),
            same(body));

    var payment =
        new PayablePaymentRequest(
            BigDecimal.ONE,
            PaymentMethodV1.CASH,
            UUID.randomUUID(),
            LocalDate.of(2026, 11, 27),
            null,
            null);
    var payable = UUID.randomUUID();
    var payments = mock(RegisterPayablePayment.class);
    new PayablePaymentController(access, payments).register(http, payable, payment);
    verify(payments).register(company, actor, payable, "request-1", payment.details());
    http.addHeader("Idempotency-Key", "duplicate");
    assertEquals(
        "EXPENSE_INVALID_IDEMPOTENCY_KEY",
        assertThrows(ExpenseFailure.class, () -> controller.create(http, body)).getMessage());
  }

  @Test
  void grantsDelegatedReadAndRejectsWriteAndForeignMembership() {
    member(Scope.READ, MembershipRole.VIEWER, true);
    member(Scope.WRITE, MembershipRole.VIEWER, true);
    assertEquals(company, access.require(request(), Scope.READ));
    assertEquals(
        ExpenseFailure.Kind.FORBIDDEN,
        assertThrows(ExpenseFailure.class, () -> access.require(request(), Scope.WRITE)).kind());
    when(memberships.membership(company, Scope.READ))
        .thenReturn(
            Optional.of(new MembershipContext(UUID.randomUUID(), MembershipRole.ADMIN, true)));
    assertThrows(ExpenseFailure.class, () -> access.require(request(), Scope.READ));
  }

  @Test
  void scopesReadOperationsAndMapsInvalidFiltersAndMissingDocuments() {
    member(Scope.READ, MembershipRole.ADMIN, false);
    var http = request();
    var expenses = mock(ExpenseQueries.class);
    var reads = new ExpenseReadController(expenses, access);
    var filters = new ExpenseReadController.Filters(null, "PURCHASE", "ALL", null, null);
    reads.page(http, filters, 1, 5);
    reads.all(http, filters);
    reads.summary(http, filters);
    verify(expenses).page(company, filters.criteria(), 1, 5);
    verify(expenses).all(company, filters.criteria());
    verify(expenses).summary(company, filters.criteria());
    assertThrows(
        ExpenseFailure.class,
        () -> reads.all(http, new ExpenseReadController.Filters("", "invalid", "", null, null)));
    var missing = assertThrows(ExpenseFailure.class, () -> reads.detail(http, UUID.randomUUID()));
    var problems = new ExpenseProblems();
    assertEquals(404, problems.expense(missing).getStatus());
    for (var kind : ExpenseFailure.Kind.values()) {
      var problem = problems.expense(new ExpenseFailure(kind, kind.name()));
      assertEquals(kind.name(), problem.getProperties().get("errorCode"));
    }
    var invalid =
        assertThrows(
            CompanySelector.InvalidContext.class,
            () -> access.require(new MockHttpServletRequest(), Scope.READ));
    assertEquals(400, problems.selection(invalid).getStatus());

    var payables = mock(PayableQueries.class);
    var cartera = new PayableController(payables, access);
    var criteria = new PayableQueries.Criteria("search", PayableQueries.Filter.PENDING, null);
    cartera.page(http, "search", PayableQueries.Filter.PENDING, null, 0, 5);
    cartera.all(http, "search", PayableQueries.Filter.PENDING, null);
    cartera.supplierBalances(http, List.of(company));
    verify(payables).page(company, criteria, 0, 5);
    verify(payables).all(company, criteria);
    verify(payables).supplierBalances(company, List.of(company));

    var snapshots = new FinancialSnapshotController(id -> id.toString(), access);
    http.setMethod("GET");
    assertEquals(company.toString(), snapshots.snapshot(http));
    http.setMethod("HEAD");
    assertNull(snapshots.snapshot(http));
  }

  private void member(Scope scope, MembershipRole role, boolean grant) {
    when(memberships.membership(company, scope))
        .thenReturn(Optional.of(new MembershipContext(company, role, grant)));
  }

  private MockHttpServletRequest request() {
    var request = new MockHttpServletRequest();
    request.addHeader(CompanySelector.HEADER, company.toString());
    request.addHeader("Idempotency-Key", "request-1");
    return request;
  }
}
