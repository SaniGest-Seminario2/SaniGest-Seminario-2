package com.sanigest.sales.application.usecase.sale;

import static com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission.SALES_READ;

import com.sanigest.sales.application.dto.SalePage;
import com.sanigest.sales.application.dto.SaleSearch;
import com.sanigest.sales.application.exception.InvalidSaleQuery;
import com.sanigest.sales.application.exception.SalesAccessForbidden;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.SaleQueryRepository;
import com.sanigest.sales.domain.enums.SaleStatus;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

public final class ListSales {
  public static final String DEFAULT_SIZE = "20";
  private static final int MAX_SEARCH_LENGTH = 250;

  private final SalesAuthorization authorization;
  private final SaleQueryRepository sales;
  private final int maxSize;

  public ListSales(SalesAuthorization authorization, SaleQueryRepository sales, int maxSize) {
    if (maxSize < Integer.parseInt(DEFAULT_SIZE)) {
      throw new IllegalArgumentException("The maximum sale page size must allow the default");
    }
    this.authorization = authorization;
    this.sales = sales;
    this.maxSize = maxSize;
  }

  public SalePage execute(
      String search, String status, LocalDate from, LocalDate to, int page, int size) {
    var companyId = requireReadCompany();
    String term = search == null ? "" : search.strip();
    LocalDate first = from == null ? SaleSearch.MIN_DATE : from;
    LocalDate last = to == null ? SaleSearch.MAX_DATE : to;
    if (term.length() > MAX_SEARCH_LENGTH
        || term.indexOf(0) >= 0
        || first.isBefore(SaleSearch.MIN_DATE)
        || last.isAfter(SaleSearch.MAX_DATE)
        || first.isAfter(last)
        || page < 0
        || size < 1
        || size > maxSize
        || (long) page * size > Integer.MAX_VALUE) {
      throw new InvalidSaleQuery();
    }
    return sales.search(
        companyId, new SaleSearch(term, parseStatus(status), first, last, page, size));
  }

  public UUID requireReadCompany() {
    var companyId = authorization.requireMembership(SALES_READ);
    if (companyId == null) {
      throw new SalesAccessForbidden();
    }
    return companyId;
  }

  private static SaleStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return SaleStatus.valueOf(status.strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException invalid) {
      throw new InvalidSaleQuery();
    }
  }
}
