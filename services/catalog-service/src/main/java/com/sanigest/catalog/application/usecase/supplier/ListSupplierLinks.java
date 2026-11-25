package com.sanigest.catalog.application.usecase.supplier;

import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;

import com.sanigest.catalog.application.dto.SupplierItemPage;
import com.sanigest.catalog.application.dto.SupplierItemQuery;
import com.sanigest.catalog.application.dto.SupplierItemQuery.Direction;
import com.sanigest.catalog.application.exception.InvalidSupplierQueryException;
import com.sanigest.catalog.application.port.out.persistence.SupplierItemRepository;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListSupplierLinks {
  private final SupplierCompany company;
  private final SupplierItemRepository items;
  private final int maxSize;

  ListSupplierLinks(
      SupplierCompany company,
      SupplierItemRepository items,
      @Value("${sanigest.catalog.suppliers.max-page-size:100}") int maxSize) {
    this.company = company;
    this.items = items;
    this.maxSize = maxSize;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  @PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
  public SupplierItemPage execute(
      UUID id, Direction direction, String search, SupplierStatus status, int page, int size) {
    if (page < 0 || size < 1 || size > maxSize || (search != null && search.indexOf(0) >= 0)) {
      throw new InvalidSupplierQueryException();
    }
    return items.search(
        new SupplierItemQuery(
            company.require(CATALOG_READ), id, direction, search, status, page, size));
  }
}
