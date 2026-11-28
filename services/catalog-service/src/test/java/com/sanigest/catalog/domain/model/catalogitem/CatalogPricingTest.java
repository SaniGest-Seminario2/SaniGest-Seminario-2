package com.sanigest.catalog.domain.model.catalogitem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanigest.catalog.domain.exception.catalogitem.InvalidCatalogItem;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CatalogPricingTest {
  @ParameterizedTest
  @CsvSource({
    "1500.00,0.180000,270.00,1770.00",
    "1500.00,0.160000,240.00,1740.00",
    "1500.00,0.000000,0.00,1500.00",
    "0.0250,0.200000,0.01,0.04",
    "0.0249,0.200000,0.00,0.02",
    "0.0050,0.000000,0.00,0.01",
    "1500.00,,,",
  })
  void calculatesReferenceTaxWithHalfUp(String price, String rate, String tax, String total) {
    var result = CatalogPricing.calculate(amount(price), null, amount(rate));
    assertThat(result.unitTaxAmount()).isEqualTo(amount(tax));
    assertThat(result.finalPrice()).isEqualTo(amount(total));
  }

  @ParameterizedTest
  @CsvSource({
    "1500.0000,1000.0000,500.0000,33.33",
    "1500.0000,1500.0000,0.0000,0.00",
    "1500.0000,2000.0000,-500.0000,-33.33",
    "0.0000,1.0000,-1.0000,",
    "100.0000,0.0000,100.0000,100.00",
    "100.0000,,,"
  })
  void calculatesGrossMarginFromNetPrice(String price, String cost, String profit, String margin) {
    var result = CatalogPricing.calculate(amount(price), amount(cost), amount("0.18"));
    assertThat(result.grossProfit()).isEqualTo(amount(profit));
    assertThat(result.grossMargin()).isEqualTo(amount(margin));
  }

  @ParameterizedTest
  @ValueSource(strings = {"-0.0001", "1.00000", "1000000000000000"})
  void rejectsAmountsOutsideV08Precision(String input) {
    assertThatThrownBy(() -> CatalogPricing.unitAmount(amount(input)))
        .isInstanceOf(InvalidCatalogItem.class);
  }

  @Test
  void normalizesEquivalentUnitAmounts() {
    assertThat(CatalogPricing.unitAmount(amount("100.0"))).isEqualTo(amount("100.0000"));
  }

  private static BigDecimal amount(String value) {
    return value == null ? null : new BigDecimal(value);
  }
}
