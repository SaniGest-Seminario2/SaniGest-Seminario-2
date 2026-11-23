package com.sanigest.sales.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.enums.SaleStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SaleTest {
  private final UUID id = UUID.randomUUID();
  private final UUID companyId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();
  private final LocalDate saleDate = LocalDate.of(2026, 11, 23);

  @Test
  void preservesCompanyClientAndImmutableLinesWithExactDopTotal() {
    SaleLine first = line(id, "0.10");
    SaleLine second = line(id, "0.20");
    List<SaleLine> lines = new ArrayList<>(List.of(first, second));
    Sale sale = sale("0.3000", lines);
    lines.clear();

    assertThat(sale.currency().getCurrencyCode()).isEqualTo("DOP");
    assertThat(sale.companyId()).isEqualTo(companyId);
    assertThat(sale.clientId()).isEqualTo(clientId);
    assertThat(sale.clientName()).isEqualTo("Cliente original");
    assertThat(sale.total()).isEqualTo(new BigDecimal("0.30"));
    assertThat(sale.lines()).containsExactly(first, second);
  }

  @Test
  void requiresIdentifiersAndSaleMetadata() {
    List<SaleLine> lines = List.of(line(id, "1"));
    assertThatNullPointerException()
        .isThrownBy(() ->
            new Sale(
                null,
                companyId,
                clientId,
                "Cliente",
                saleDate,
                SaleMode.CASH,
                SaleStatus.REGISTERED,
                BigDecimal.ONE,
                lines));
    assertThatNullPointerException()
        .isThrownBy(() ->
            new Sale(
                id,
                null,
                clientId,
                "Cliente",
                saleDate,
                SaleMode.CASH,
                SaleStatus.REGISTERED,
                BigDecimal.ONE,
                lines));
    assertThatNullPointerException()
        .isThrownBy(() ->
            new Sale(
                id,
                companyId,
                null,
                "Cliente",
                saleDate,
                SaleMode.CASH,
                SaleStatus.REGISTERED,
                BigDecimal.ONE,
                lines));
    assertThatNullPointerException()
        .isThrownBy(() ->
            new Sale(
                id,
                companyId,
                clientId,
                "Cliente",
                null,
                SaleMode.CASH,
                SaleStatus.REGISTERED,
                BigDecimal.ONE,
                lines));
    assertThatNullPointerException()
        .isThrownBy(() ->
            new Sale(
                id,
                companyId,
                clientId,
                "Cliente",
                saleDate,
                null,
                SaleStatus.REGISTERED,
                BigDecimal.ONE,
                lines));
    assertThatNullPointerException()
        .isThrownBy(() ->
            new Sale(
                id,
                companyId,
                clientId,
                "Cliente",
                saleDate,
                SaleMode.CASH,
                null,
                BigDecimal.ONE,
                lines));
    assertThatIllegalArgumentException()
        .isThrownBy(() ->
            new Sale(
                id,
                companyId,
                clientId,
                "Cliente",
                LocalDate.MAX,
                SaleMode.CASH,
                SaleStatus.REGISTERED,
                BigDecimal.ONE,
                lines));
  }

  @Test
  void rejectsInconsistentOrOutOfRangeTotals() {
    List<SaleLine> lines = List.of(line(id, "1.25"));
    assertThatIllegalArgumentException().isThrownBy(() -> sale("1.24", lines));
    assertThatIllegalArgumentException().isThrownBy(() -> sale("1.251", lines));
    assertThatIllegalArgumentException().isThrownBy(() -> sale("-1", lines));
    assertThatIllegalArgumentException()
        .isThrownBy(() ->
            new Sale(
                id,
                companyId,
                clientId,
                "Cliente",
                saleDate,
                SaleMode.CASH,
                SaleStatus.REGISTERED,
                null,
                lines));
    List<SaleLine> overflowing =
        List.of(line(id, "50000000000000000"), line(id, "50000000000000000"));
    assertThatIllegalArgumentException().isThrownBy(() -> sale("100000000000000000", overflowing));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> sale("99999999999999999.99", overflowing));
  }

  @Test
  void requiresNonemptyDistinctLinesOwnedByTheSale() {
    SaleLine first = line(id, "1");
    assertThatIllegalArgumentException().isThrownBy(() -> sale("0", List.of()));
    assertThatIllegalArgumentException().isThrownBy(() -> sale("2", List.of(first, first)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> sale("1", List.of(line(UUID.randomUUID(), "1"))));
  }

  private Sale sale(String total, List<SaleLine> lines) {
    return new Sale(
        id,
        companyId,
        clientId,
        "Cliente original",
        saleDate,
        SaleMode.CREDIT,
        SaleStatus.REGISTERED,
        new BigDecimal(total),
        lines);
  }

  private SaleLine line(UUID saleId, String price) {
    BigDecimal amount = new BigDecimal(price);
    return new SaleLine(
        UUID.randomUUID(),
        saleId,
        UUID.randomUUID(),
        "Producto",
        null,
        amount,
        BigDecimal.ONE,
        amount);
  }
}
