package com.sanigest.catalog.domain.model.catalogitem;

import static com.sanigest.catalog.domain.enums.catalogitem.CatalogItemType.PRODUCT;
import static com.sanigest.catalog.domain.enums.catalogitem.CatalogItemType.SERVICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanigest.catalog.domain.exception.catalogitem.InvalidCatalogItem;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CatalogItemTest {
  private final UUID company = UUID.randomUUID();

  @ParameterizedTest
  @CsvSource({
    "0,0.00",
    "0.00,0.00",
    "1,1.00",
    "1.5,1.50",
    "1.50,1.50",
    "1.500,1.50",
    "15000,15000.00",
    "15000.00,15000.00",
    "3200.00,3200.00",
    "999999999999.99,999999999999.99",
    "1.2345,1.2345",
    "999999999999999.9999,999999999999999.9999"
  })
  void registersProductsWithExactPrices(String input, String expected) {
    var item = CatalogItem.register(company, "  Chair  ", PRODUCT, new BigDecimal(input));
    assertThat(item.id()).isNotNull();
    assertThat(item.companyId()).isEqualTo(company);
    assertThat(item.name()).isEqualTo("Chair");
    assertThat(item.type()).isEqualTo(PRODUCT);
    assertThat(item.price()).isEqualTo(new BigDecimal(expected));
    assertThat(item.currency()).isEqualTo("DOP");
    assertThat(item.active()).isTrue();
  }

  @Test
  void registersServicesWithDistinctServerIds() {
    var item = CatalogItem.register(company, "Consulting", SERVICE, BigDecimal.ONE);
    assertThat(item.type()).isEqualTo(SERVICE);
    assertThat(item.id())
        .isNotEqualTo(CatalogItem.register(company, "Consulting", SERVICE, BigDecimal.ONE).id());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"-0.01", "10.99999", "1000000000000000", "1e-2147483647", "1e2147483647"})
  void rejectsInvalidPrices(String price) {
    assertThatThrownBy(() ->
            CatalogItem.register(
                company, "Chair", PRODUCT, price == null ? null : new BigDecimal(price)))
        .isInstanceOf(InvalidCatalogItem.class);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " \t\n", "bad\0name"})
  void rejectsInvalidNames(String name) {
    assertThatThrownBy(() -> CatalogItem.register(company, name, PRODUCT, BigDecimal.ONE))
        .isInstanceOf(InvalidCatalogItem.class);
  }

  @Test
  void rejectsMissingIdentityOrTypeAndNamesBeyondTheStorageLimit() {
    assertThatThrownBy(() -> new CatalogItem(null, company, "Chair", PRODUCT, BigDecimal.ONE, true))
        .isInstanceOf(InvalidCatalogItem.class);
    assertThatThrownBy(() -> CatalogItem.register(null, "Chair", PRODUCT, BigDecimal.ONE))
        .isInstanceOf(InvalidCatalogItem.class);
    assertThatThrownBy(() -> CatalogItem.register(company, "Chair", null, BigDecimal.ONE))
        .isInstanceOf(InvalidCatalogItem.class);
    assertThatThrownBy(() ->
            CatalogItem.register(
                company, "a".repeat(CatalogItem.NAME_LIMIT + 1), PRODUCT, BigDecimal.ONE))
        .isInstanceOf(InvalidCatalogItem.class);
  }

  @Test
  void patchesOnlyPresentFieldsAndAllowsClearingDescription() {
    var item =
        CatalogItem.register(UUID.randomUUID(), "Silla", PRODUCT, new BigDecimal("3200.50"))
            .withActive(false);
    var described =
        item.apply(
            new CatalogItemPatch(
                Optional.empty(), Optional.of(Optional.of("Silla gris")), Optional.empty()));
    assertThat(described.description()).isEqualTo("Silla gris");
    assertThat(described.name()).isEqualTo(item.name());
    assertThat(described.price()).isEqualTo(item.price());

    var repriced =
        described.apply(
            new CatalogItemPatch(
                Optional.of(" Silla de oficina "),
                Optional.empty(),
                Optional.of(new BigDecimal("3500"))));
    assertThat(repriced.description()).isEqualTo("Silla gris");
    assertThat(repriced.name()).isEqualTo("Silla de oficina");
    assertThat(repriced.price()).isEqualTo(new BigDecimal("3500.00"));
    assertThat(repriced.id()).isEqualTo(item.id());
    assertThat(repriced.companyId()).isEqualTo(item.companyId());
    assertThat(repriced.type()).isEqualTo(item.type());
    assertThat(repriced.currency()).isEqualTo("DOP");
    assertThat(repriced.active()).isFalse();
    assertThat(
            repriced
                .apply(
                    new CatalogItemPatch(
                        Optional.empty(), Optional.of(Optional.empty()), Optional.empty()))
                .description())
        .isNull();
  }

  @Test
  void appliesTheSameInvariantsToEdits() {
    var item = CatalogItem.register(UUID.randomUUID(), "Silla", PRODUCT, BigDecimal.ONE);
    for (var invalid : java.util.List.of("-1", "10.99999", "1000000000000000")) {
      assertThatThrownBy(() ->
              item.apply(
                  new CatalogItemPatch(
                      Optional.empty(), Optional.empty(), Optional.of(new BigDecimal(invalid)))))
          .isInstanceOf(InvalidCatalogItem.class);
    }
    assertThatThrownBy(() ->
            item.apply(new CatalogItemPatch(Optional.of(" "), Optional.empty(), Optional.empty())))
        .isInstanceOf(InvalidCatalogItem.class);
    assertThatThrownBy(() ->
            item.apply(
                new CatalogItemPatch(
                    Optional.empty(),
                    Optional.of(Optional.of("x".repeat(1001))),
                    Optional.empty())))
        .isInstanceOf(InvalidCatalogItem.class);
  }
}
