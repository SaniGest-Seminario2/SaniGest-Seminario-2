package com.sanigest.company.api.controller.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.company.application.account.AccountType;
import com.sanigest.company.application.exception.access.AuthorizationUnavailableException;
import com.sanigest.company.application.exception.access.CompanyAccessDeniedException;
import com.sanigest.company.application.port.out.integration.account.CurrentAccount;
import com.sanigest.company.application.port.out.persistence.team.MembershipRepository;
import com.sanigest.company.application.port.out.persistence.team.MembershipRepository.ActiveAccess;
import com.sanigest.company.application.usecase.access.RequireRegularAccount;
import com.sanigest.company.application.usecase.access.ResolveCompanyAccess;
import com.sanigest.company.application.usecase.advisor.CheckAdvisorPermissionUseCase;
import com.sanigest.company.domain.enums.team.CompanyRole;
import com.sanigest.company.domain.model.advisor.AdvisorPermission;
import com.sanigest.companyaccess.application.port.out.CompanyAccess;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class CompanyAccessControllerTest {
  private static final UUID USER = UUID.randomUUID();
  private static final UUID COMPANY = UUID.randomUUID();
  private static final String SESSION = "session-only-for-test";
  private final MembershipRepository memberships = mock(MembershipRepository.class);
  private final CheckAdvisorPermissionUseCase advisors = mock(CheckAdvisorPermissionUseCase.class);
  private final JwtDecoder users = mock(JwtDecoder.class);
  private final CurrentAccount accounts = mock(CurrentAccount.class);
  private final CompanyAccessController controller =
      new CompanyAccessController(
          new ResolveCompanyAccess(memberships, advisors),
          users,
          new RequireRegularAccount(accounts));

  @Test
  void reportsOnlyTheCurrentMembershipAndExplicitGrant() {
    session(USER);
    when(accounts.resolve(SESSION)).thenReturn(AccountType.REGULAR);
    when(memberships.findActiveAccess(USER, COMPANY, "CATALOG_WRITE"))
        .thenReturn(Optional.of(new ActiveAccess(COMPANY, CompanyRole.OPERATOR, true, "Empresa")));
    var response = controller.resolve(assertion(null), SESSION);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(response.getBody())
        .satisfies(body -> {
          assertThat(body.companyId()).isEqualTo(COMPANY);
          assertThat(body.role()).isEqualTo(CompanyAccess.MembershipRole.OPERATOR);
          assertThat(body.explicitGrant()).isTrue();
          assertThat(body.companyName()).isNull();
        });
    verifyNoInteractions(advisors);
  }

  @Test
  void resolvesFinanceWriteUsingTheCashGrant() {
    session(USER);
    when(accounts.resolve(SESSION)).thenReturn(AccountType.REGULAR);
    when(memberships.findActiveAccess(USER, COMPANY, "CASH_WRITE"))
        .thenReturn(Optional.of(new ActiveAccess(COMPANY, CompanyRole.OPERATOR, true, "Empresa")));
    var response = controller.resolve(assertion(null, "FINANCE"), SESSION);
    assertThat(response.getBody().explicitGrant()).isTrue();
  }

  @Test
  void includesCompanyNameInSalesWriteMembership() {
    session(USER);
    when(accounts.resolve(SESSION)).thenReturn(AccountType.REGULAR);
    when(memberships.findActiveAccess(USER, COMPANY, "SALES_WRITE"))
        .thenReturn(Optional.of(new ActiveAccess(COMPANY, CompanyRole.OWNER, false, "Empresa")));
    var response = controller.resolve(assertion(null, "SALES"), SESSION);
    assertThat(response.getBody().companyId()).isEqualTo(COMPANY);
    assertThat(response.getBody().companyName()).isEqualTo("Empresa");
  }

  @Test
  void deniesMissingMismatchedInvalidAndNonRegularSessionsBeforeMembershipLookup() {
    assertThatThrownBy(() -> controller.resolve(assertion(null), null))
        .isInstanceOf(CompanyAccessDeniedException.class);
    when(users.decode(SESSION)).thenThrow(new BadJwtException("invalid"));
    assertThatThrownBy(() -> controller.resolve(assertion(null), SESSION))
        .isInstanceOf(CompanyAccessDeniedException.class);
    doReturn(sessionJwt(UUID.randomUUID())).when(users).decode(SESSION);
    assertThatThrownBy(() -> controller.resolve(assertion(null), SESSION))
        .isInstanceOf(CompanyAccessDeniedException.class);
    session(USER);
    when(accounts.resolve(SESSION)).thenReturn(AccountType.ADVISOR);
    assertThatThrownBy(() -> controller.resolve(assertion(null), SESSION))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verifyNoInteractions(memberships, advisors);
  }

  @Test
  void distinguishesMissingMembershipFromAnUnavailableAuthority() {
    session(USER);
    when(accounts.resolve(SESSION)).thenReturn(AccountType.REGULAR);
    assertThatThrownBy(() -> controller.resolve(assertion(null), SESSION))
        .isInstanceOf(CompanyAccessDeniedException.class);
    when(accounts.resolve(SESSION)).thenThrow(new AuthorizationUnavailableException());
    assertThatThrownBy(() -> controller.resolve(assertion(null), SESSION))
        .isInstanceOf(AuthorizationUnavailableException.class);
  }

  @Test
  void delegatedAccessChecksTheSignedTargetAndScope() {
    var response = controller.resolve(assertion(COMPANY), null);
    assertThat(response.getBody())
        .satisfies(body -> {
          assertThat(body.mode())
              .isEqualTo(
                  com.sanigest.companyaccess.infrastructure.integration.protocol
                      .CompanyAccessProtocol.AccessMode.DELEGATED);
          assertThat(body.companyId()).isEqualTo(COMPANY);
        });
    verify(advisors)
        .require(
            USER,
            COMPANY,
            new AdvisorPermission(AdvisorPermission.Module.CATALOG, AdvisorPermission.Scope.WRITE));
    verifyNoInteractions(users, accounts, memberships);
  }

  @Test
  void inventoryResolvesMembershipWithoutGrantingDelegation() {
    session(USER);
    when(accounts.resolve(SESSION)).thenReturn(AccountType.REGULAR);
    when(memberships.findActiveAccess(USER, COMPANY, "INVENTORY_WRITE"))
        .thenReturn(Optional.of(new ActiveAccess(COMPANY, CompanyRole.ADMIN, false, "Empresa")));
    var body = controller.resolve(assertion(null, "INVENTORY"), SESSION).getBody();
    assertThat(body.module()).isEqualTo(CompanyAccess.Module.INVENTORY);
    assertThat(body.role()).isEqualTo(CompanyAccess.MembershipRole.ADMIN);
    assertThat(body.explicitGrant()).isFalse();
    assertThatThrownBy(() -> controller.resolve(assertion(COMPANY, "INVENTORY"), null))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verifyNoInteractions(advisors);
  }

  private void session(UUID user) {
    when(users.decode(SESSION)).thenReturn(sessionJwt(user));
  }

  private static Jwt sessionJwt(UUID user) {
    return Jwt.withTokenValue(SESSION).header("alg", "RS256").subject(user.toString()).build();
  }

  private static Jwt assertion(UUID target) {
    return assertion(target, "CATALOG");
  }

  private static Jwt assertion(UUID target, String module) {
    var jwt =
        Jwt.withTokenValue("service-assertion")
            .header("alg", "RS256")
            .subject(USER.toString())
            .claim("module", module)
            .claim("scope", "WRITE")
            .claim("company_id", (target == null ? COMPANY : target).toString())
            .claim("mode", target == null ? "MEMBERSHIP" : "DELEGATED");
    return jwt.build();
  }
}
