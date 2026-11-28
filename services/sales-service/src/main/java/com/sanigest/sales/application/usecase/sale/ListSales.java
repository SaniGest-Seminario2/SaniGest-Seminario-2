package com.sanigest.sales.application.usecase.sale;

import static com.sanigest.sales.application.port.out.integration.company.SalesAuthorization.Permission.SALES_READ;

import com.sanigest.sales.application.dto.sale.SalePage;
import com.sanigest.sales.application.dto.sale.SaleSearch;
import com.sanigest.sales.application.dto.sale.SaleTotals;
import com.sanigest.sales.application.exception.sale.InvalidSaleQuery;
import com.sanigest.sales.application.exception.sale.SaleListLimitExceeded;
import com.sanigest.sales.application.exception.security.SalesAccessForbidden;
import com.sanigest.sales.application.port.out.integration.company.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.sale.SaleQueryRepository;
import com.sanigest.sales.domain.enums.sale.SaleMode;
import com.sanigest.sales.domain.enums.sale.SaleStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ListSales {
  public static final String DEFAULT_SIZE = "20";
  public static final int MAX_UNPAGED_RESULTS = 1000;
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
    return execute(search, status, null, from, to, page, size);
  }

  public SalePage execute(
      String search, String status, String mode, LocalDate from, LocalDate to, int page, int size) {
    var companyId = requireReadCompany();
    if (page < 0 || size < 1 || size > maxSize || (long) page * size > Integer.MAX_VALUE) {
      throw new InvalidSaleQuery();
    }
    return sales.search(companyId, criteria(search, status, mode, from, to, page, size));
  }

  public List<SalePage.Item> all(
      String search, String status, String mode, LocalDate from, LocalDate to) {
    var companyId = requireReadCompany();
    var result =
        sales.search(companyId, criteria(search, status, mode, from, to, 0, MAX_UNPAGED_RESULTS));
    if (result.totalElements() > MAX_UNPAGED_RESULTS) {
      throw new SaleListLimitExceeded();
    }
    return result.content();
  }

  public SaleTotals summary(
      String search, String status, String mode, LocalDate from, LocalDate to) {
    return sales.totals(requireReadCompany(), criteria(search, status, mode, from, to, 0, 1));
  }

  private static SaleSearch criteria(
      String search, String status, String mode, LocalDate from, LocalDate to, int page, int size) {
    String term = search == null ? "" : search.strip();
    LocalDate first = from == null ? SaleSearch.MIN_DATE : from;
    LocalDate last = to == null ? SaleSearch.MAX_DATE : to;
    if (term.length() > MAX_SEARCH_LENGTH
        || term.indexOf(0) >= 0
        || first.isBefore(SaleSearch.MIN_DATE)
        || last.isAfter(SaleSearch.MAX_DATE)
        || first.isAfter(last)) {
      throw new InvalidSaleQuery();
    }
    return new SaleSearch(
        term,
        parseFilter(SaleStatus.class, status),
        first,
        last,
        page,
        size,
        parseFilter(SaleMode.class, mode));
  }

  public UUID requireReadCompany() {
    var companyId = authorization.requireMembership(SALES_READ);
    if (companyId == null) {
      throw new SalesAccessForbidden();
    }
    return companyId;
  }

  private static <E extends Enum<E>> E parseFilter(Class<E> type, String value) {
    if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.strip())) {
      return null;
    }
    try {
      return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException invalid) {
      throw new InvalidSaleQuery();
    }
  }
}
