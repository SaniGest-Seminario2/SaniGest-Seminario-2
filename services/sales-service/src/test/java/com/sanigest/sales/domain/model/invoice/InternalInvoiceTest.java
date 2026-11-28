package com.sanigest.sales.domain.model.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sanigest.sales.domain.enums.sale.SaleMode;
import com.sanigest.sales.domain.model.catalog.CatalogCustomerSnapshot;
import com.sanigest.sales.domain.model.invoice.InternalInvoice.CompanySnapshot;
import com.sanigest.sales.domain.model.sale.Sale;
import com.sanigest.sales.domain.model.sale.SaleCancellation;
import com.sanigest.sales.domain.model.sale.SaleLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InternalInvoiceTest {
  private static final UUID ACTOR = UUID.randomUUID();
  private static final Instant ISSUED_AT = Instant.parse("2027-01-01T03:59:59Z");
  private static final String NUMBER = "FAC-2026-001";
  private final UUID saleId = UUID.randomUUID();
  private final CompanySnapshot company =
      new CompanySnapshot(UUID.randomUUID(), "Empresa histórica");
  private final CatalogCustomerSnapshot customer =
      new CatalogCustomerSnapshot(
          UUID.randomUUID(), "Cliente histórico", CatalogCustomerSnapshot.Status.ACTIVE);
  private final SaleLine line =
      new SaleLine(
          UUID.randomUUID(),
          saleId,
          UUID.randomUUID(),
          "Artículo",
          "Descripción",
          new BigDecimal("12.50"),
          new BigDecimal("1.2"),
          new BigDecimal("15.00"));
  private final Sale sale =
      Sale.register(
          saleId, company.id(), customer, LocalDate.of(2026, 11, 23), SaleMode.CASH, List.of(line));

  @Test
  void copiesExactHistoricalDataAndUsesASeparateDocumentStatus() {
    var invoice = InternalInvoice.issue(sale, company, NUMBER, ISSUED_AT, ACTOR);
    assertThat(invoice.saleId()).isEqualTo(saleId);
    assertThat(invoice.saleDate()).isEqualTo(sale.saleDate());
    assertThat(invoice.issuedAt()).isEqualTo(ISSUED_AT);
    assertThat(invoice.issuedBy()).isEqualTo(ACTOR);
    assertThat(invoice.company()).isEqualTo(company);
    assertThat(invoice.client().id()).isEqualTo(customer.id());
    assertThat(invoice.client().name()).isEqualTo(customer.name());
    assertThat(invoice.lines()).containsExactly(InternalInvoiceLine.from(line));
    assertThat(invoice.total()).isEqualTo(new BigDecimal("15.00"));
    assertThat(invoice.currency()).isEqualTo("DOP");
    assertThat(invoice.documentType()).isEqualTo(InternalInvoice.DocumentType.INTERNAL);
    assertThat(invoice.fiscalClassification())
        .isEqualTo(InternalInvoice.FiscalClassification.NOT_FISCAL);
    assertThat(invoice.status()).isEqualTo(InternalInvoice.Status.ISSUED);
    var mutable = new ArrayList<>(invoice.lines());
    var copy = rebuild(invoice, mutable, invoice.total());
    mutable.clear();
    assertThat(copy.lines()).isEqualTo(invoice.lines());
  }

  @Test
  void rejectsInvalidSourceNumberSnapshotsAndAmounts() {
    var cancelled = sale.cancel(new SaleCancellation(ACTOR, ISSUED_AT, "Duplicada"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> InternalInvoice.issue(cancelled, company, NUMBER, ISSUED_AT, ACTOR));
    assertThatIllegalArgumentException()
        .isThrownBy(() ->
            InternalInvoice.issue(
                sale, new CompanySnapshot(UUID.randomUUID(), "Otra"), NUMBER, ISSUED_AT, ACTOR));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> InternalInvoice.issue(sale, company, "FAC-2027-001", ISSUED_AT, ACTOR));
    assertThatIllegalArgumentException().isThrownBy(() -> new CompanySnapshot(company.id(), " "));
    var invoice = InternalInvoice.issue(sale, company, NUMBER, ISSUED_AT, ACTOR);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> rebuild(invoice, List.of(), BigDecimal.ZERO));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> rebuild(invoice, invoice.lines(), new BigDecimal("15.001")));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> rebuild(invoice, invoice.lines(), new BigDecimal("16.00")));
    assertThatIllegalArgumentException()
        .isThrownBy(() ->
            new InternalInvoiceLine(
                line.id(),
                line.catalogItemId(),
                line.itemName(),
                null,
                line.unitPrice(),
                line.quantity(),
                BigDecimal.ZERO));
  }

  private static InternalInvoice rebuild(
      InternalInvoice invoice, List<InternalInvoiceLine> lines, BigDecimal total) {
    return new InternalInvoice(
        invoice.id(),
        invoice.saleId(),
        invoice.number(),
        invoice.issuedAt(),
        invoice.saleDate(),
        invoice.issuedBy(),
        invoice.company(),
        invoice.client(),
        lines,
        total);
  }
}
