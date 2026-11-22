package com.sanigest.catalog.application.usecase.client;

import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;

import com.sanigest.catalog.application.dto.ClientPage;
import com.sanigest.catalog.application.exception.ClientListingForbiddenException;
import com.sanigest.catalog.application.exception.InvalidClientQueryException;
import com.sanigest.catalog.application.port.out.persistence.ClientRepository;
import com.sanigest.catalog.domain.enums.ClientStatus;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ListClients {
  public static final String DEFAULT_SIZE = "20";

  private final ClientCompany company;
  private final ClientRepository clients;
  private final int maxSize;

  public ListClients(
      ClientCompany company,
      ClientRepository clients,
      @Value("${sanigest.catalog.clients.max-page-size:100}") int maxSize) {
    if (maxSize < Integer.parseInt(DEFAULT_SIZE)) {
      throw new IllegalArgumentException("The maximum client page size must allow the default");
    }
    this.company = company;
    this.clients = clients;
    this.maxSize = maxSize;
  }

  @PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
  public ClientPage execute(String search, String status, int page, int size) {
    if (page < 0 || size < 1 || size > maxSize) {
      throw new InvalidClientQueryException();
    }
    var term = optionalFilter(search);
    if (term != null && term.indexOf(0) >= 0) {
      throw new InvalidClientQueryException();
    }
    var state = optionalFilter(status);
    if (state != null) {
      try {
        state = ClientStatus.valueOf(state.toUpperCase(Locale.ROOT)).name();
      } catch (IllegalArgumentException invalid) {
        throw new InvalidClientQueryException();
      }
    }
    var companyId =
        company.membership(CATALOG_READ).orElseThrow(ClientListingForbiddenException::new);
    return clients.search(companyId, term, state, page, size);
  }

  private static String optionalFilter(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
