package com.sanigest.expense.api.controller.expense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sanigest.companyaccess.application.port.out.CompanyAccess;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.MembershipContext;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.Scope;
import com.sanigest.expense.api.dto.expense.CreateExpenseRequest;
import com.sanigest.expense.api.security.ExpenseCompanyAccess;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.RegistrationKey;
import com.sanigest.expense.application.exception.ExpenseFailure;
import com.sanigest.expense.application.port.in.expense.CancelExpense;
import com.sanigest.expense.application.port.in.expense.ExpenseQueries;
import com.sanigest.expense.application.usecase.expense.RegisterExpense;
import com.sanigest.security.api.request.company.CompanySelector;
import java.time.Instant;
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

class ExpenseControllerTest {
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
    var controller =
        new ExpenseController(expenses, mock(CancelExpense.class), access, new JsonMapper());
    var body =
        new CreateExpenseRequest(
            null, null, null, null, null, null, null, null, null, null, null, List.of());
    controller.create(http, body);
    verify(expenses)
        .register(
            eq(new RegistrationKey(company, actor, "request-1")),
            matches("[a-f0-9]{64}"),
            eq(body.toCommand()));

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
    assertEquals(
        "EXPENSE_NOT_FOUND",
        assertThrows(ExpenseFailure.class, () -> reads.detail(http, UUID.randomUUID()))
            .getMessage());
    assertThrows(
        CompanySelector.InvalidContext.class,
        () -> access.require(new MockHttpServletRequest(), Scope.READ));
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
