package com.sanigest.sales.infrastructure.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.companyaccess.application.port.out.CompanyAccess;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.MembershipContext;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.Scope;
import com.sanigest.sales.application.exception.security.SalesAccessForbidden;
import com.sanigest.sales.application.exception.security.SalesAuthorizationUnavailable;
import com.sanigest.sales.application.port.out.integration.company.SalesAuthorization.Permission;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CompanySalesAuthorizationTest {
  private final UUID company = UUID.randomUUID();
  private final CompanyAccess client = mock(CompanyAccess.class);
  private final DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
  private final CompanySalesAuthorization authorization =
      new CompanySalesAuthorization(beans.getBeanProvider(CompanyAccess.class), () -> company);

  @AfterEach
  void clearSession() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @CsvSource({
    "OWNER, false, SALES_READ, READ, true",
    "OWNER, false, SALES_WRITE, WRITE, true",
    "OWNER, false, SALES_CANCEL, WRITE, true",
    "ADMIN, false, SALES_READ, READ, true",
    "ADMIN, false, SALES_WRITE, WRITE, true",
    "ADMIN, false, SALES_CANCEL, WRITE, true",
    "OPERATOR, false, SALES_READ, READ, true",
    "OPERATOR, false, SALES_WRITE, WRITE, false",
    "OPERATOR, true, SALES_WRITE, WRITE, true",
    "OPERATOR, true, SALES_CANCEL, WRITE, false",
    "VIEWER, false, SALES_READ, READ, true",
    "VIEWER, true, SALES_WRITE, WRITE, false",
    "VIEWER, true, SALES_CANCEL, WRITE, false"
  })
  void appliesMembershipPolicy(
      MembershipRole role, boolean grant, Permission permission, Scope scope, boolean allowed) {
    authenticate();
    beans.registerSingleton("companyAccess", client);
    when(client.membership(company, scope))
        .thenReturn(Optional.of(new MembershipContext(company, role, grant)));

    if (allowed) {
      assertThat(authorization.requireMembership(permission)).isEqualTo(company);
    } else {
      assertThatThrownBy(() -> authorization.requireMembership(permission))
          .isInstanceOf(SalesAccessForbidden.class);
    }
  }

  @Test
  void deniesWithoutAnAuthenticatedActorOrDelegatedTenant() {
    beans.registerSingleton("companyAccess", client);
    assertThatThrownBy(() -> authorization.requireMembership(Permission.SALES_READ))
        .isInstanceOf(SalesAccessForbidden.class);
    assertThatThrownBy(
            () -> authorization.requireDelegation(UUID.randomUUID(), Permission.SALES_READ))
        .isInstanceOf(SalesAccessForbidden.class);
    authenticate();
    assertThatThrownBy(() -> authorization.requireDelegation(null, Permission.SALES_READ))
        .isInstanceOf(SalesAccessForbidden.class);
    assertThatThrownBy(
            () -> authorization.requireDelegation(UUID.randomUUID(), Permission.SALES_CANCEL))
        .isInstanceOf(SalesAccessForbidden.class);
    verifyNoInteractions(client);
  }

  @Test
  void reportsUnavailableWhenCompanyIntegrationIsDisabled() {
    authenticate();
    assertThatThrownBy(() -> authorization.requireMembership(Permission.SALES_WRITE))
        .isInstanceOf(SalesAuthorizationUnavailable.class);
  }

  private static void authenticate() {
    var jwt =
        Jwt.withTokenValue("unit-test")
            .header("alg", "RS256")
            .subject(UUID.randomUUID().toString())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
  }
}
