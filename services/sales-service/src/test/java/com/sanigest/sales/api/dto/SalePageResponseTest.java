package com.sanigest.sales.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanigest.sales.application.dto.SalePage;
import com.sanigest.sales.domain.enums.ReceivableStatus;
import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.enums.SaleStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SalePageResponseTest {
  @Test
  void mapsTheSnapshotAndSerializesExactMoneyAndPageMetadata() {
    var item =
        new SalePage.Item(
            UUID.randomUUID(),
            "V-2026-001",
            UUID.randomUUID(),
            "Cliente",
            LocalDate.of(2026, 11, 23),
            SaleMode.CASH,
            SaleStatus.REGISTERED,
            new BigDecimal("99999999999999999.99"),
            "FAC-000001",
            ReceivableStatus.OPEN);
    var json =
        new ObjectMapper().valueToTree(SalePageResponse.from(new SalePage(List.of(item), 2, 2, 5)));
    var summary = json.path("content").get(0);
    assertThat(summary.path("id").asString()).isEqualTo(item.id().toString());
    assertThat(summary.path("client").path("id").asString()).isEqualTo(item.clientId().toString());
    assertThat(summary.path("client").path("name").asString()).isEqualTo(item.clientName());
    assertThat(summary.path("total").isString()).isTrue();
    assertThat(summary.path("total").asString()).isEqualTo(item.total().toPlainString());
    assertThat(summary.path("currency").asString()).isEqualTo("DOP");
    assertThat(summary.path("status").asString()).isEqualTo("REGISTERED");
    assertThat(summary.path("paymentStatus").asString()).isEqualTo("OPEN");
    assertThat(summary.path("invoiceNumber").asString()).isEqualTo("FAC-000001");
    assertThat(json.path("page").asInt()).isEqualTo(2);
    assertThat(json.path("size").asInt()).isEqualTo(2);
    assertThat(json.path("totalElements").asLong()).isEqualTo(5);
    assertThat(json.path("totalPages").asLong()).isEqualTo(3);
  }
}
