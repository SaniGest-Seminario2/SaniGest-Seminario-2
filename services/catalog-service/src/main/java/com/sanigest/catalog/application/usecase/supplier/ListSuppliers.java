package com.sanigest.catalog.application.usecase.supplier;

import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;

import com.sanigest.catalog.application.dto.SupplierPage;
import com.sanigest.catalog.application.exception.InvalidSupplierQueryException;
import com.sanigest.catalog.application.port.out.persistence.SupplierRepository;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ListSuppliers {
  public static final String DEFAULT_SIZE = "20";

  private final SupplierCompany company;
  private final SupplierRepository suppliers;
  private final int maxSize;

  ListSuppliers(
      SupplierCompany company,
      SupplierRepository suppliers,
      @Value("${sanigest.catalog.suppliers.max-page-size:100}") int maxSize) {
    if (maxSize < Integer.parseInt(DEFAULT_SIZE)) {
      throw new IllegalArgumentException("The maximum supplier page size must allow the default");
    }
    this.company = company;
    this.suppliers = suppliers;
    this.maxSize = maxSize;
  }

  @PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
  public SupplierPage execute(String search, String status, int page, int size) {
    if (page < 0 || size < 1 || size > maxSize) {
      throw new InvalidSupplierQueryException();
    }
    var term = optionalFilter(search);
    if (term != null && term.indexOf(0) >= 0) {
      throw new InvalidSupplierQueryException();
    }
    var state = parseStatus(optionalFilter(status));
    return suppliers.search(company.require(CATALOG_READ), term, state, page, size);
  }

  private static SupplierStatus parseStatus(String value) {
    try {
      return value == null ? null : SupplierStatus.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException invalid) {
      throw new InvalidSupplierQueryException();
    }
  }

  private static String optionalFilter(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
