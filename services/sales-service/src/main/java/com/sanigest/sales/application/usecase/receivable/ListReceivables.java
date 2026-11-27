package com.sanigest.sales.application.usecase.receivable;

import static com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission.SALES_READ;

import com.sanigest.sales.application.dto.ReceivableDetail;
import com.sanigest.sales.application.dto.ReceivablePage;
import com.sanigest.sales.application.dto.ReceivableSearch;
import com.sanigest.sales.application.dto.ReceivableSearch.Filter;
import com.sanigest.sales.application.exception.InvalidSaleQuery;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.ReceivableRepository;
import java.util.List;

public final class ListReceivables {
  private final SalesAuthorization authorization;
  private final ReceivableRepository receivables;
  private final int maxPageSize;

  public ListReceivables(
      SalesAuthorization authorization, ReceivableRepository receivables, int maxPageSize) {
    this.authorization = authorization;
    this.receivables = receivables;
    this.maxPageSize = maxPageSize;
  }

  public ReceivablePage page(String search, Filter status, int page, int size) {
    var company = authorization.requireMembership(SALES_READ);
    if (page < 0 || size < 1 || size > maxPageSize) {
      throw new InvalidSaleQuery();
    }
    return receivables.page(company, criteria(search, status), page, size);
  }

  public List<ReceivableDetail> all(String search, Filter status) {
    var company = authorization.requireMembership(SALES_READ);
    return receivables.all(company, criteria(search, status));
  }

  private static ReceivableSearch criteria(String search, Filter status) {
    String term = search.strip();
    if (term.length() > 250 || term.indexOf(0) >= 0) {
      throw new InvalidSaleQuery();
    }
    return new ReceivableSearch(term, status);
  }
}
