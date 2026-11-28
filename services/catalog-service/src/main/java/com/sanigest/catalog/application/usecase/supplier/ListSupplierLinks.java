package com.sanigest.catalog.application.usecase.supplier;

import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_READ;

import com.sanigest.catalog.application.dto.supplier.SupplierItemPage;
import com.sanigest.catalog.application.dto.supplier.SupplierItemQuery;
import com.sanigest.catalog.application.dto.supplier.SupplierItemQuery.Direction;
import com.sanigest.catalog.application.exception.supplier.InvalidSupplierQueryException;
import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierItemRepository;
import com.sanigest.catalog.domain.enums.supplier.SupplierStatus;
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
  @PreAuthorize(
      "T(com.sanigest.companyaccess.security.authentication.AuthenticatedActor).userId().isPresent()")
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
