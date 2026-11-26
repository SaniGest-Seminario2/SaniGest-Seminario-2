package com.sanigest.inventory.infrastructure.integration.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sanigest.inventory.infrastructure.integration.catalog.CatalogHttpApi.ItemResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

class CatalogInventoryClientTest {
  private final UUID tenant = UUID.randomUUID();
  private final UUID item = UUID.randomUUID();
  private final CatalogHttpApi wire = mock(CatalogHttpApi.class);
  private final CatalogHttpClient client = new CatalogHttpClient(wire);

  @BeforeEach
  void authenticate() {
    var jwt =
        Jwt.withTokenValue("test")
            .header("alg", "RS256")
            .subject(UUID.randomUUID().toString())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void checksBothIdentityAndTenantAndKeepsUnitMetadata() {
    var response = new ItemResponse(item, "PRODUCT", false, "KG", true, "SKU", "Product");
    reply(tenant, response);
    assertThat(client.find(tenant, item).divisible()).isTrue();
    assertThat(client.find(tenant, item).active()).isFalse();
    reply(UUID.randomUUID(), response);
    assertThatThrownBy(() -> client.find(tenant, item)).hasMessage("CATALOG_UNAVAILABLE");
    reply(
        tenant,
        new ItemResponse(UUID.randomUUID(), "PRODUCT", true, "UNIT", false, "SKU", "Product"));
    assertThatThrownBy(() -> client.find(tenant, item)).hasMessage("CATALOG_UNAVAILABLE");
    reply(tenant, null);
    assertThatThrownBy(() -> client.find(tenant, item)).hasMessage("CATALOG_UNAVAILABLE");
  }

  @ParameterizedTest
  @CsvSource({
    "401,CATALOG_ACCESS_DENIED",
    "403,CATALOG_ACCESS_DENIED",
    "404,CATALOG_ITEM_NOT_FOUND",
    "503,CATALOG_UNAVAILABLE",
    "409,CATALOG_UNAVAILABLE"
  })
  void mapsDependencyErrors(int status, String code) {
    when(wire.get(eq(item), eq(tenant), eq(tenant), eq("Bearer test"), anyString(), anyString()))
        .thenThrow(
            new RestClientResponseException(
                "failure", HttpStatusCode.valueOf(status), "", new HttpHeaders(), null, null));
    assertThatThrownBy(() -> client.find(tenant, item)).hasMessage(code);
  }

  @Test
  void rejectsTransportFailureAndUnauthenticatedCalls() {
    when(wire.get(eq(item), eq(tenant), eq(tenant), eq("Bearer test"), anyString(), anyString()))
        .thenThrow(new ResourceAccessException("timeout"));
    assertThatThrownBy(() -> client.find(tenant, item)).hasMessage("CATALOG_UNAVAILABLE");
    clear();
    assertThatThrownBy(() -> client.find(tenant, item)).hasMessage("CATALOG_ACCESS_DENIED");
  }

  private void reply(UUID tenantHeader, ItemResponse value) {
    when(wire.get(eq(item), eq(tenant), eq(tenant), eq("Bearer test"), anyString(), anyString()))
        .thenReturn(
            ResponseEntity.ok()
                .header("X-Catalog-Company-Id", tenantHeader.toString())
                .body(value));
  }
}
