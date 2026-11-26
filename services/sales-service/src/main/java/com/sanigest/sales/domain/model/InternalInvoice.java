package com.sanigest.sales.domain.model;

import com.sanigest.sales.domain.enums.SaleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record InternalInvoice(
    UUID id,
    UUID saleId,
    String number,
    Instant issuedAt,
    LocalDate saleDate,
    UUID issuedBy,
    CompanySnapshot company,
    ClientSnapshot client,
    List<InternalInvoiceLine> lines,
    BigDecimal total) {

  public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Santo_Domingo");

  public enum DocumentType {
    INTERNAL
  }

  public enum FiscalClassification {
    NOT_FISCAL
  }

  public enum Status {
    ISSUED
  }

  public DocumentType documentType() {
    return DocumentType.INTERNAL;
  }

  public FiscalClassification fiscalClassification() {
    return FiscalClassification.NOT_FISCAL;
  }

  public Status status() {
    return Status.ISSUED;
  }

  public String currency() {
    return "DOP";
  }

  public static boolean isEligible(Sale sale) {
    return sale.status() == SaleStatus.REGISTERED;
  }

  public static InternalInvoice issue(
      Sale sale, CompanySnapshot company, String number, Instant at, UUID actor) {
    if (!isEligible(sale) || !sale.companyId().equals(company.id())) {
      throw new IllegalArgumentException("Invalid invoice source");
    }
    return new InternalInvoice(
        UUID.randomUUID(),
        sale.id(),
        number,
        at,
        sale.saleDate(),
        actor,
        company,
        new ClientSnapshot(sale.clientId(), sale.clientName()),
        sale.lines().stream().map(InternalInvoiceLine::from).toList(),
        sale.total());
  }

  public InternalInvoice {
    Objects.requireNonNull(id);
    Objects.requireNonNull(saleId);
    Objects.requireNonNull(issuedAt);
    Sale.validateDate(Objects.requireNonNull(saleDate));
    Objects.requireNonNull(issuedBy);
    Objects.requireNonNull(company);
    Objects.requireNonNull(client);
    if (number == null
        || !number.matches("FAC-[0-9]{4}-[0-9]{3,19}")
        || Integer.parseInt(number.substring(4, 8)) != issuedAt.atZone(BUSINESS_ZONE).getYear()
        || Long.parseLong(number.substring(9)) <= 0) {
      throw new IllegalArgumentException("Invalid invoice number");
    }
    lines = List.copyOf(lines);
    total = SaleAmounts.amount(total, "total");
    if (lines.isEmpty()
        || lines.stream().map(InternalInvoiceLine::id).distinct().count() != lines.size()
        || total.compareTo(
                lines.stream()
                    .map(InternalInvoiceLine::total)
                    .reduce(BigDecimal.ZERO, BigDecimal::add))
            != 0) {
      throw new IllegalArgumentException("Invalid invoice lines or total");
    }
  }

  public record ClientSnapshot(UUID id, String name) {
    public ClientSnapshot {
      Objects.requireNonNull(id);
      name = SnapshotText.name(name, 250, "clientName");
    }
  }

  public record CompanySnapshot(UUID id, String name) {
    public CompanySnapshot {
      Objects.requireNonNull(id);
      name = SnapshotText.name(name, 250, "companyName");
    }
  }
}
