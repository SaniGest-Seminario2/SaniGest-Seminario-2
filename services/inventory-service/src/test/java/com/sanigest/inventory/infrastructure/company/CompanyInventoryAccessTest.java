package com.sanigest.inventory.infrastructure.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sanigest.companyaccess.CompanyAccess;
import com.sanigest.companyaccess.CompanyAccess.MembershipContext;
import com.sanigest.companyaccess.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.CompanyAccess.Scope;
import com.sanigest.companyaccess.CompanyAccessUnavailable;
import com.sanigest.inventory.application.port.out.integration.InventoryAccess.Operation;
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

class CompanyInventoryAccessTest {
  private final UUID company = UUID.randomUUID();
  private final UUID user = UUID.randomUUID();
  private final CompanyAccess companyAccess = mock(CompanyAccess.class);
  private final CompanyInventoryAccess policy = new CompanyInventoryAccess(companyAccess);

  @BeforeEach
  void authenticate() {
    var jwt =
        Jwt.withTokenValue("test")
            .header("alg", "RS256")
            .subject(user.toString())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @CsvSource({
    "OWNER,READ,false,true",
    "ADMIN,MANAGE_WAREHOUSE,false,true",
    "OWNER,CONFIGURE_STOCK,false,true",
    "ADMIN,ADJUST_STOCK,false,true",
    "VIEWER,READ,false,true",
    "VIEWER,ADJUST_STOCK,true,false",
    "OPERATOR,READ,true,true",
    "OPERATOR,READ,false,false",
    "OPERATOR,ADJUST_STOCK,true,false"
  })
  void checksExactRoleAndOperation(
      MembershipRole role, Operation operation, boolean grant, boolean allowed) {
    var scope = operation == Operation.READ ? Scope.READ : Scope.WRITE;
    when(companyAccess.membership(company, scope))
        .thenReturn(Optional.of(new MembershipContext(company, role, grant)));
    if (allowed) {
      assertThat(policy.authorize(company, operation)).isEqualTo(user);
    } else {
      assertThatThrownBy(() -> policy.authorize(company, operation))
          .hasMessage("INVENTORY_ACCESS_DENIED");
    }
  }

  @Test
  void failsClosedForMissingWrongOrUnavailableMembership() {
    assertThatThrownBy(() -> policy.authorize(company, Operation.READ))
        .hasMessage("INVENTORY_ACCESS_DENIED");
    when(companyAccess.membership(company, Scope.READ))
        .thenReturn(
            Optional.of(new MembershipContext(UUID.randomUUID(), MembershipRole.OWNER, true)));
    assertThatThrownBy(() -> policy.authorize(company, Operation.READ))
        .hasMessage("INVENTORY_ACCESS_DENIED");
    when(companyAccess.membership(company, Scope.READ)).thenThrow(new CompanyAccessUnavailable());
    assertThatThrownBy(() -> policy.authorize(company, Operation.READ))
        .hasMessage("INVENTORY_AUTHORIZATION_UNAVAILABLE");
    clear();
    assertThatThrownBy(() -> policy.authorize(company, Operation.READ))
        .hasMessage("INVENTORY_ACCESS_DENIED");
  }
}
