package com.sanigest.sales.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SaleLineTest {
  private final UUID id = UUID.randomUUID();
  private final UUID saleId = UUID.randomUUID();
  private final UUID itemId = UUID.randomUUID();

  @Test
  void preservesSnapshotAndNormalizesExactDecimals() {
    SaleLine line = line("12.500", "1.20", "15.0000");
    assertThat(line.itemName()).isEqualTo("  Servicio al vender  ");
    assertThat(line.itemDescription()).isEqualTo("Descripción original");
    assertThat(line.catalogItemId()).isEqualTo(itemId);
    assertThat(line.unitPrice()).isEqualTo(new BigDecimal("12.50"));
    assertThat(line.quantity()).isEqualTo(new BigDecimal("1.200"));
    assertThat(line.subtotal()).isEqualTo(new BigDecimal("15.00"));
    assertThat(line("0", "0.001", "0").subtotal()).isEqualTo(new BigDecimal("0.00"));
    assertThat(line("99999999999999999.99", "1", "99999999999999999.99").subtotal())
        .isEqualTo(new BigDecimal("99999999999999999.99"));
  }

  @ParameterizedTest
  @CsvSource({
    "1,0,0",
    "1,-1,0",
    "0,0.0001,0",
    "0,1000000000000,0",
    "-1,1,0",
    "0.001,1,0",
    "1,1,2",
    "0.01,0.5,0.01",
    "1,1,-1",
    "100000000000000000,1,0",
    "50000000000000000,2,100000000000000000",
    "50000000000000000,2,99999999999999999.99"
  })
  void rejectsInvalidAmounts(String price, String quantity, String subtotal) {
    assertThatIllegalArgumentException().isThrownBy(() -> line(price, quantity, subtotal));
  }

  @Test
  void requiresIdentifiers() {
    assertThatNullPointerException().isThrownBy(() -> withIds(null, saleId, itemId));
    assertThatNullPointerException().isThrownBy(() -> withIds(id, null, itemId));
    assertThatNullPointerException().isThrownBy(() -> withIds(id, saleId, null));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" \t", "nombre\0"})
  void requiresAValidSnapshotName(String name) {
    assertThatIllegalArgumentException()
        .isThrownBy(() ->
            new SaleLine(
                id, saleId, itemId, name, null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
  }

  private SaleLine withIds(UUID lineId, UUID ownerId, UUID catalogItemId) {
    return new SaleLine(
        lineId,
        ownerId,
        catalogItemId,
        "Servicio",
        null,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE);
  }

  private SaleLine line(String price, String quantity, String subtotal) {
    return new SaleLine(
        id,
        saleId,
        itemId,
        "  Servicio al vender  ",
        "Descripción original",
        new BigDecimal(price),
        new BigDecimal(quantity),
        new BigDecimal(subtotal));
  }
}
