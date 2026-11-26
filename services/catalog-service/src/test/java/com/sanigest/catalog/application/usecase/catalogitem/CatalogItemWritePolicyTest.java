package com.sanigest.catalog.application.usecase.catalogitem;

import static com.sanigest.catalog.domain.enums.CatalogItemType.SERVICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sanigest.catalog.application.port.out.persistence.CatalogItemCommercialRepository;
import com.sanigest.catalog.domain.exception.InvalidCatalogItem;
import com.sanigest.catalog.domain.model.CatalogItem;
import com.sanigest.catalog.domain.model.CatalogItemProfile;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogItemWritePolicyTest {
  private final CatalogBusinessDate date =
      new CatalogBusinessDate(Clock.fixed(Instant.parse("2026-06-01T03:59:59Z"), ZoneOffset.UTC));
  private final CatalogItemCommercialRepository references =
      mock(CatalogItemCommercialRepository.class);

  @Test
  void usesMidnightInSantoDomingo() {
    assertThat(date.today()).isEqualTo(LocalDate.of(2026, 5, 31));
  }

  @Test
  void gatesUnsupportedPricesUntilPricingIsActivatedForSales() {
    when(references.activeUnit("UNIT")).thenReturn(true);
    when(references.currentTax("EXEMPT", date.today())).thenReturn(true);
    var item =
        new CatalogItem(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Servicio",
            SERVICE,
            new BigDecimal("10.0001"),
            true,
            null,
            new CatalogItemProfile(null, null, null, "UNIT", null, "EXEMPT", true, false));
    var legacy = new CatalogItemWritePolicy(references, date, false);
    assertThatThrownBy(() -> legacy.validate(null, item)).isInstanceOf(InvalidCatalogItem.class);

    var enabled = new CatalogItemWritePolicy(references, date, true);
    assertThatCode(() -> enabled.validate(null, item)).doesNotThrowAnyException();
    assertThatCode(() -> legacy.validate(item, item.withActive(false))).doesNotThrowAnyException();
  }
}
