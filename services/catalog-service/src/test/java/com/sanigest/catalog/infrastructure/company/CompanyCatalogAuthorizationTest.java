package com.sanigest.catalog.infrastructure.company;

import static com.sanigest.catalog.application.CatalogAuthorization.Permission.CATALOG_READ;
import static com.sanigest.catalog.application.CatalogAuthorization.Permission.CATALOG_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.catalog.application.ClientCreationException;
import com.sanigest.companyaccess.CompanyAccess;
import com.sanigest.companyaccess.CompanyAccess.MembershipContext;
import com.sanigest.companyaccess.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.CompanyAccess.Scope;
import com.sanigest.companyaccess.CompanyAccessUnavailable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CompanyCatalogAuthorizationTest {
  private final CompanyAccess access = mock(CompanyAccess.class);
  private final CompanyCatalogAuthorization authorization = new CompanyCatalogAuthorization(access);
  private final UUID companyId = UUID.randomUUID();

  @BeforeEach
  void authenticate() {
    var token =
        Jwt.withTokenValue("session")
            .header("alg", "RS256")
            .subject(UUID.randomUUID().toString())
            .expiresAt(Instant.now().plusSeconds(120))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(token, List.of()));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @CsvSource({
    "OWNER,false,true",
    "ADMIN,false,true",
    "OPERATOR,false,false",
    "OPERATOR,true,true",
    "VIEWER,false,false",
    "VIEWER,true,false"
  })
  void appliesMembershipPolicy(MembershipRole role, boolean grant, boolean canWrite) {
    when(access.membership(Scope.READ))
        .thenReturn(Optional.of(new MembershipContext(companyId, role, false)));
    when(access.membership(Scope.WRITE))
        .thenReturn(Optional.of(new MembershipContext(companyId, role, grant)));

    assertThat(authorization.membership(CATALOG_READ)).contains(companyId);
    assertThat(authorization.membership(CATALOG_WRITE))
        .isEqualTo(canWrite ? Optional.of(companyId) : Optional.empty());
  }

  @Test
  void requiresExactDelegatedCompanyAndScopeAndChecksRevocation() {
    when(access.delegated(companyId, Scope.READ)).thenReturn(true, false);
    when(access.delegated(companyId, Scope.WRITE)).thenReturn(false, true, false);

    assertThat(authorization.delegated(companyId, CATALOG_READ)).contains(companyId);
    assertThat(authorization.delegated(companyId, CATALOG_WRITE)).isEmpty();
    assertThat(authorization.delegated(companyId, CATALOG_WRITE)).contains(companyId);
    assertThat(authorization.delegated(UUID.randomUUID(), CATALOG_WRITE)).isEmpty();
    assertThat(authorization.delegated(companyId, CATALOG_READ)).isEmpty();
    assertThat(authorization.delegated(companyId, CATALOG_WRITE)).isEmpty();
  }

  @Test
  void checksMembershipAgainAfterRevocation() {
    when(access.membership(Scope.WRITE))
        .thenReturn(Optional.of(new MembershipContext(companyId, MembershipRole.OWNER, false)))
        .thenReturn(Optional.empty());
    assertThat(authorization.membership(CATALOG_WRITE)).contains(companyId);
    assertThat(authorization.membership(CATALOG_WRITE)).isEmpty();
  }

  @Test
  void deniesMissingActorAndMissingDelegatedTarget() {
    assertThat(authorization.delegated(null, CATALOG_READ)).isEmpty();
    clearAuthentication();
    assertThat(authorization.membership(CATALOG_READ)).isEmpty();
    assertThat(authorization.delegated(companyId, CATALOG_WRITE)).isEmpty();
    verifyNoInteractions(access);
  }

  @Test
  void failsClosedAndPreservesTechnicalFailures() {
    when(access.membership(Scope.WRITE)).thenThrow(new CompanyAccessUnavailable());
    when(access.delegated(companyId, Scope.READ)).thenThrow(new CompanyAccessUnavailable());

    assertThatThrownBy(() -> authorization.membership(CATALOG_WRITE))
        .isInstanceOfSatisfying(
            ClientCreationException.class,
            failure ->
                assertThat(failure.reason())
                    .isEqualTo(ClientCreationException.Reason.AUTHORIZATION_UNAVAILABLE));
    assertThatThrownBy(() -> authorization.delegated(companyId, CATALOG_READ))
        .isInstanceOfSatisfying(
            ClientCreationException.class,
            failure ->
                assertThat(failure.reason())
                    .isEqualTo(ClientCreationException.Reason.AUTHORIZATION_UNAVAILABLE));
  }
}
