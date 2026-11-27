package com.sanigest.catalog.application.usecase.client;

import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;

import com.sanigest.catalog.application.dto.ClientPage;
import com.sanigest.catalog.application.dto.ClientSearchCriteria;
import com.sanigest.catalog.application.exception.ClientAccessException;
import com.sanigest.catalog.application.exception.ClientFinancialsUnavailable;
import com.sanigest.catalog.application.exception.ClientListingForbiddenException;
import com.sanigest.catalog.application.exception.InvalidClientQueryException;
import com.sanigest.catalog.application.port.out.integration.SalesClientFinancials;
import com.sanigest.catalog.application.port.out.persistence.ClientRepository;
import com.sanigest.catalog.domain.enums.ClientStatus;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ListClients {
  public static final String DEFAULT_SIZE = "20";

  private final ClientCompany company;
  private final ClientRepository clients;
  private final int maxSize;
  private final SalesClientFinancials sales;

  public ListClients(
      ClientCompany company,
      ClientRepository clients,
      SalesClientFinancials sales,
      @Value("${sanigest.catalog.clients.max-page-size:100}") int maxSize) {
    if (maxSize < Integer.parseInt(DEFAULT_SIZE)) {
      throw new IllegalArgumentException("The maximum client page size must allow the default");
    }
    this.company = company;
    this.clients = clients;
    this.maxSize = maxSize;
    this.sales = sales;
  }

  @PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
  public ClientPage execute(ClientSearchCriteria criteria, int page, int size) {
    if (page < 0 || size < 1 || size > maxSize) {
      throw new InvalidClientQueryException();
    }
    return search(criteria, page, size);
  }

  @PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
  public List<ClientPage.Item> all(ClientSearchCriteria criteria) {
    return search(criteria, 0, Integer.MAX_VALUE).content();
  }

  private ClientPage search(ClientSearchCriteria criteria, int page, int size) {
    var term = optionalFilter(criteria.search());
    if (term != null && term.indexOf(0) >= 0) {
      throw new InvalidClientQueryException();
    }
    var state = optionalFilter(criteria.status());
    if (state != null) {
      try {
        state = ClientStatus.valueOf(state.toUpperCase(Locale.ROOT)).name();
      } catch (IllegalArgumentException invalid) {
        throw new InvalidClientQueryException();
      }
    }
    var companyId =
        company.membership(CATALOG_READ).orElseThrow(ClientListingForbiddenException::new);
    var result =
        clients.search(
            companyId,
            new ClientSearchCriteria(
                term,
                state,
                criteria.clientType(),
                criteria.categoryId(),
                criteria.paymentTerms(),
                criteria.descending()),
            page,
            size);
    return withBalances(companyId, result);
  }

  private ClientPage withBalances(UUID companyId, ClientPage page) {
    if (page.content().isEmpty()) {
      return page;
    }
    var balances = new HashMap<UUID, BigDecimal>();
    var ids = page.content().stream().map(ClientPage.Item::id).toList();
    try {
      for (int start = 0; start < ids.size(); ) {
        int end = start + Math.min(500, ids.size() - start);
        balances.putAll(sales.balances(companyId, ids.subList(start, end)));
        start = end;
      }
    } catch (ClientFinancialsUnavailable | ClientAccessException failure) {
      return page;
    }
    var content =
        page.content().stream()
            .map(item -> item.withBalance(balances.getOrDefault(item.id(), BigDecimal.ZERO)))
            .toList();
    return new ClientPage(content, page.page(), page.size(), page.totalElements());
  }

  private static String optionalFilter(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
