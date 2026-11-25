package com.sanigest.catalog.application.usecase.catalogitem;

import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;

import com.sanigest.catalog.application.dto.CatalogItemPage;
import com.sanigest.catalog.application.dto.CatalogItemSearchCriteria;
import com.sanigest.catalog.application.exception.InvalidCatalogItemQuery;
import com.sanigest.catalog.application.port.out.persistence.CatalogItemRepository;
import com.sanigest.catalog.domain.enums.CatalogItemType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ListCatalogItems {
  public static final String DEFAULT_SIZE = "20";
  public static final int MAX_SIZE = 100;

  private final CatalogItemCompany company;
  private final CatalogItemRepository items;

  ListCatalogItems(CatalogItemCompany company, CatalogItemRepository items) {
    this.company = company;
    this.items = items;
  }

  @PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
  public CatalogItemPage execute(String type, String search, String active, int page, int size) {
    var companyId = company.require(CATALOG_READ);
    if (page < 0 || size < 1 || size > MAX_SIZE) {
      throw new InvalidCatalogItemQuery();
    }
    var term = search == null || search.isBlank() ? null : search.strip();
    if (term != null && term.indexOf(0) >= 0) {
      throw new InvalidCatalogItemQuery();
    }
    return items.search(companyId, parseType(type), term, parseActive(active), page, size);
  }

  @PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
  public CatalogItemPage execute(
      String type, String search, String active, CommercialFilters filters, int page, int size) {
    var companyId = company.require(CATALOG_READ);
    if (page < 0 || size < 1 || size > MAX_SIZE || (search != null && search.indexOf(0) >= 0)) {
      throw new InvalidCatalogItemQuery();
    }
    var term = search == null || search.isBlank() ? null : search.strip();
    return items.search(
        companyId,
        new CatalogItemSearchCriteria(
            parseType(type),
            term,
            parseActive(active),
            filters.categoryId(),
            parseActive(filters.availableForSale()),
            parseActive(filters.availableForPurchase())),
        page,
        size);
  }

  public record CommercialFilters(
      java.util.UUID categoryId, String availableForSale, String availableForPurchase) {}

  private static CatalogItemType parseType(String type) {
    if (type == null) {
      return null;
    }
    try {
      return CatalogItemType.valueOf(type);
    } catch (IllegalArgumentException invalid) {
      throw new InvalidCatalogItemQuery();
    }
  }

  private static Boolean parseActive(String active) {
    if (active == null) {
      return null;
    }
    return switch (active) {
      case "true" -> true;
      case "false" -> false;
      default -> throw new InvalidCatalogItemQuery();
    };
  }
}
