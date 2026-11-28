package com.sanigest.sales.application.dto.sale;

import com.sanigest.sales.domain.enums.sale.SaleMode;
import com.sanigest.sales.domain.enums.sale.SaleStatus;
import java.time.LocalDate;

public record SaleSearch(
    String search,
    SaleStatus status,
    LocalDate from,
    LocalDate to,
    int page,
    int size,
    SaleMode mode) {
  public static final LocalDate MIN_DATE = LocalDate.of(1, 1, 1);
  public static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

  public SaleSearch(
      String search, SaleStatus status, LocalDate from, LocalDate to, int page, int size) {
    this(search, status, from, to, page, size, null);
  }

  public long offset() {
    return (long) page * size;
  }
}
