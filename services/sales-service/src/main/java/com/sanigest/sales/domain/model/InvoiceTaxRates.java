package com.sanigest.sales.domain.model;

import java.math.BigDecimal;

public final class InvoiceTaxRates {
  public static final BigDecimal STANDARD = new BigDecimal("0.18");
  public static final BigDecimal REDUCED = new BigDecimal("0.16");
  public static final BigDecimal EXEMPT = BigDecimal.ZERO;

  private InvoiceTaxRates() {}

  public static boolean accepts(BigDecimal rate) {
    return rate == null
        || rate.compareTo(EXEMPT) == 0
        || rate.compareTo(REDUCED) == 0
        || rate.compareTo(STANDARD) == 0;
  }
}
