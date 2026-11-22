package com.sanigest.catalog.infrastructure.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.catalog.application.ClientCreationException;
import com.sanigest.catalog.application.ClientCreationException.Reason;
import com.sanigest.catalog.application.ClientRepository;
import com.sanigest.catalog.application.CreateClient;
import com.sanigest.catalog.domain.ClientDetails;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CompanyClientCreationAuthorizationTest {
  private static final UUID USER = UUID.randomUUID();
  private static final UUID TENANT = UUID.randomUUID();
  private static final String SESSION = "verified";
  private final CompanyAccess company = mock(CompanyAccess.class);
  private final CompanyClientCreationAuthorization authorization =
      new CompanyClientCreationAuthorization(company);

  @BeforeEach
  void authenticated() {
    var session =
        Jwt.withTokenValue(SESSION)
            .header("alg", "RS256")
            .subject(USER.toString())
            .claim("companyId", UUID.randomUUID().toString())
            .claim("role", "OWNER")
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(session, List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void requiresTheAuthenticatedUserAndSession() {
    assertThat(authorization.authorizedCompany(UUID.randomUUID(), SESSION)).isEmpty();
    assertThat(authorization.authorizedCompany(USER, "altered")).isEmpty();
    assertThat(authorization.authorizedCompany(USER, null)).isEmpty();
    verifyNoInteractions(company);
  }

  @Test
  void appliesTheLocalCreatePolicyToTheCurrentMembership() {
    for (var role : MembershipRole.values()) {
      for (boolean explicit : new boolean[] {false, true}) {
        when(company.membership(Scope.WRITE))
            .thenReturn(Optional.of(new MembershipContext(TENANT, role, explicit)));
        var actual = authorization.authorizedCompany(USER, SESSION);
        if (role == MembershipRole.OWNER
            || role == MembershipRole.ADMIN
            || role == MembershipRole.OPERATOR && explicit) {
          assertThat(actual).contains(TENANT);
        } else {
          assertThat(actual).isEmpty();
        }
      }
    }
  }

  @Test
  void preventsInsertsOnDenialAndUnavailableAuthority() {
    var clients = mock(ClientRepository.class);
    var create = new CreateClient(Optional.of(authorization), clients);
    when(company.membership(Scope.WRITE)).thenReturn(Optional.empty());
    assertFailure(create, Reason.FORBIDDEN);
    when(company.membership(Scope.WRITE)).thenThrow(new CompanyAccessUnavailable());
    assertFailure(create, Reason.AUTHORIZATION_UNAVAILABLE);
    verifyNoInteractions(clients);
  }

  private static void assertFailure(CreateClient create, Reason reason) {
    assertThatThrownBy(() ->
            create.execute(USER, SESSION, new ClientDetails("Client", null, null, null, null)))
        .isInstanceOfSatisfying(
            ClientCreationException.class,
            failure -> assertThat(failure.reason()).isEqualTo(reason));
  }
}
