package com.sanigest.catalog.application.usecase.client;

import static com.sanigest.catalog.application.exception.ClientCreationException.Reason.AUTHORIZATION_UNAVAILABLE;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.catalog.application.dto.ClientPage;
import com.sanigest.catalog.application.dto.ClientSearchCriteria;
import com.sanigest.catalog.application.exception.ClientCreationException;
import com.sanigest.catalog.application.exception.InvalidClientQueryException;
import com.sanigest.catalog.application.port.out.integration.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.integration.SalesClientFinancials;
import com.sanigest.catalog.application.port.out.persistence.ClientRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.AccessDeniedException;

class ListClientsTest {
  private final CatalogAuthorization authorization = mock(CatalogAuthorization.class);
  private final ClientRepository repository = mock(ClientRepository.class);
  private final SalesClientFinancials sales = mock(SalesClientFinancials.class);
  private final ListClients useCase =
      new ListClients(new ClientCompany(Optional.of(authorization)), repository, sales, 100);

  @ParameterizedTest
  @CsvSource({
    ", , , , true",
    "'  ', ' \t ', , , true",
    "' Clínica ', , Clínica, , true",
    "10100, , 10100, , true",
    "missing, , missing, , false",
    ", ACTIVE, , ACTIVE, true",
    ", INACTIVE, , INACTIVE, false",
    "' 10100 ', ' active ', 10100, ACTIVE, true"
  })
  void queriesOnlyTheTenantAuthorizedForRead(
      String search, String status, String term, String state, boolean match) {
    var company = UUID.randomUUID();
    var items =
        match
            ? List.of(
                new ClientPage.Item(
                    UUID.randomUUID(),
                    "Clínica",
                    "101001",
                    "ACTIVE",
                    null,
                    null,
                    null,
                    null,
                    null,
                    java.math.BigDecimal.ZERO),
                new ClientPage.Item(
                    UUID.randomUUID(),
                    "Clínica",
                    "101002",
                    "ACTIVE",
                    null,
                    null,
                    null,
                    null,
                    null,
                    java.math.BigDecimal.ZERO))
            : List.<ClientPage.Item>of();
    var result = new ClientPage(items, 0, 20, items.size());
    when(authorization.membership(CATALOG_READ)).thenReturn(Optional.of(company));
    when(repository.search(
            company, new ClientSearchCriteria(term, state, null, null, null, false), 0, 20))
        .thenReturn(result);

    var page =
        useCase.execute(new ClientSearchCriteria(search, status, null, null, null, false), 0, 20);
    assertThat(page).isEqualTo(result);
    assertThat(page.content()).containsExactlyElementsOf(items);
    assertThat(page.totalPages()).isEqualTo(match ? 1 : 0);
  }

  @ParameterizedTest
  @CsvSource({
    "-1, 20, ACTIVE",
    "0, 0, ACTIVE",
    "0, -1, ACTIVE",
    "0, 101, ACTIVE",
    "0, 20, DELETED"
  })
  void rejectsInvalidQueries(int page, int size, String status) {
    assertThatThrownBy(() ->
            useCase.execute(
                new ClientSearchCriteria(null, status, null, null, null, false), page, size))
        .isInstanceOf(InvalidClientQueryException.class);
    verifyNoInteractions(authorization, repository);
  }

  @Test
  void deniesReadWhenCompanyDoesNotAuthorizeIt() {
    when(authorization.membership(CATALOG_READ)).thenReturn(Optional.empty());

    assertThatThrownBy(() ->
            useCase.execute(new ClientSearchCriteria(null, null, null, null, null, false), 0, 20))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void failsClosedWhenCompanyIsUnavailable() {
    var unavailable = new ClientCreationException(AUTHORIZATION_UNAVAILABLE);
    when(authorization.membership(CATALOG_READ)).thenThrow(unavailable);

    assertThatThrownBy(() ->
            useCase.execute(new ClientSearchCriteria(null, null, null, null, null, false), 0, 20))
        .isSameAs(unavailable);
    verifyNoInteractions(repository);
  }
}
