package com.sanigest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.web.audit.AuditPage;
import com.sanigest.web.audit.AuditPage.Filters;
import com.sanigest.web.audit.AuditPage.Reader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CompanyAuditEndpointTest {
  private static final CompanyAuditParameters DEFAULTS =
      new CompanyAuditParameters(null, null, null, null, null, null, null, null);
  private final UUID company = UUID.randomUUID();
  private final Reader reader = mock(Reader.class);

  @Test
  void readsTheAuthorizedTenantWithDefaultPaginationAndDisablesCaching() {
    var page = new AuditPage(List.of(), 0, 20, false);
    when(reader.read(company, new Filters(null, null, null, null, null, null, 0, 20)))
        .thenReturn(page);
    var endpoint = new CompanyAuditEndpoint(company::equals, reader) {};
    var response = endpoint.get(headers(), DEFAULTS);
    assertThat(response.getBody()).isEqualTo(page);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
  }

  @Test
  void rejectsUnauthorizedReadsAndInvalidInputBeforeQuerying() {
    var endpoint = new CompanyAuditEndpoint(tenant -> false, reader) {};
    assertStatus(() -> endpoint.get(headers(), DEFAULTS), HttpStatus.FORBIDDEN);
    assertStatus(() -> endpoint.get(new HttpHeaders(), DEFAULTS), HttpStatus.BAD_REQUEST);
    var invalid = new CompanyAuditParameters(null, null, null, null, null, null, -1, 101);
    assertStatus(() -> endpoint.get(headers(), invalid), HttpStatus.BAD_REQUEST);
    verifyNoInteractions(reader);
  }

  private HttpHeaders headers() {
    var headers = new HttpHeaders();
    headers.set(CompanySelector.HEADER, company.toString());
    return headers;
  }

  private static void assertStatus(Runnable action, HttpStatus expected) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            failure -> assertThat(failure.getStatusCode()).isEqualTo(expected));
  }
}
