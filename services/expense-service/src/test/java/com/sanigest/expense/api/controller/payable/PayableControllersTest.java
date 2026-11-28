package com.sanigest.expense.api.controller.payable;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sanigest.companyaccess.application.port.out.CompanyAccess;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.MembershipContext;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.Scope;
import com.sanigest.events.domain.enums.payment.PaymentMethodV1;
import com.sanigest.expense.api.dto.payable.PayablePaymentRequest;
import com.sanigest.expense.api.security.ExpenseCompanyAccess;
import com.sanigest.expense.application.port.in.payable.PayableQueries;
import com.sanigest.expense.application.usecase.payable.RegisterPayablePayment;
import com.sanigest.security.api.request.company.CompanySelector;
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

class PayableControllersTest {
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
  void passesTenantActorAndKeyToPayablePayment() {
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
  }

  @Test
  void scopesPayableReads() {
    member(Scope.READ, MembershipRole.ADMIN, false);
    var http = request();
    var payables = mock(PayableQueries.class);
    var cartera = new PayableController(payables, access);
    var criteria = new PayableQueries.Criteria("search", PayableQueries.Filter.PENDING, null);
    cartera.page(http, "search", PayableQueries.Filter.PENDING, null, 0, 5);
    cartera.all(http, "search", PayableQueries.Filter.PENDING, null);
    cartera.supplierBalances(http, List.of(company));
    verify(payables).page(company, criteria, 0, 5);
    verify(payables).all(company, criteria);
    verify(payables).supplierBalances(company, List.of(company));
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
