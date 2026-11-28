package com.sanigest.expense.api.controller.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sanigest.companyaccess.application.port.out.CompanyAccess;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.MembershipContext;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.Scope;
import com.sanigest.expense.api.security.ExpenseCompanyAccess;
import com.sanigest.security.api.request.company.CompanySelector;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;

class FinancialSnapshotControllerTest {
  private final UUID company = UUID.randomUUID();
  private final CompanyAccess memberships = mock();
  private final ExpenseCompanyAccess access =
      new ExpenseCompanyAccess(
          new StaticListableBeanFactory(java.util.Map.of("memberships", memberships))
              .getBeanProvider(CompanyAccess.class));

  @Test
  void authorizesFinancialSnapshots() {
    member(Scope.READ, MembershipRole.ADMIN, false);
    var http = request();
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
