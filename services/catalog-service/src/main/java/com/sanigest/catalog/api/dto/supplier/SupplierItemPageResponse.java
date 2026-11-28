package com.sanigest.catalog.api.dto.supplier;

import com.sanigest.catalog.application.dto.supplier.SupplierItemPage;
import com.sanigest.catalog.domain.enums.supplier.SupplierStatus;
import com.sanigest.web.api.dto.PageResponse;
import java.time.Instant;
import java.util.UUID;

public final class SupplierItemPageResponse {
  private SupplierItemPageResponse() {}

  public static PageResponse<SuppliedItem> items(SupplierItemPage page) {
    return new PageResponse<>(
        page.content().stream()
            .map(entry -> {
              var party = entry.party();
              return new SuppliedItem(
                  party.id(),
                  party.name(),
                  party.type(),
                  state(party.active()),
                  Offer.from(entry.offer()));
            })
            .toList(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages());
  }

  public static PageResponse<ItemSupplier> suppliers(SupplierItemPage page) {
    return new PageResponse<>(
        page.content().stream()
            .map(entry -> {
              var party = entry.party();
              return new ItemSupplier(
                  party.id(), party.name(), state(party.active()), Offer.from(entry.offer()));
            })
            .toList(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages());
  }

  public record SuppliedItem(
      UUID catalogItemId,
      String name,
      String type,
      SupplierStatus itemStatus,
      @com.fasterxml.jackson.annotation.JsonUnwrapped Offer offer) {}

  public record ItemSupplier(
      UUID supplierId,
      String name,
      SupplierStatus supplierStatus,
      @com.fasterxml.jackson.annotation.JsonUnwrapped Offer offer) {}

  public record Offer(
      String offeredCost,
      String supplierReference,
      boolean primary,
      SupplierStatus status,
      Instant validFrom,
      Instant validUntil) {
    static Offer from(SupplierItemPage.Offer offer) {
      return new Offer(
          offer.offeredCost() == null ? null : offer.offeredCost().toPlainString(),
          offer.supplierReference(),
          offer.primary(),
          state(offer.validUntil() == null),
          offer.validFrom(),
          offer.validUntil());
    }
  }

  private static SupplierStatus state(boolean active) {
    return active ? SupplierStatus.ACTIVE : SupplierStatus.INACTIVE;
  }
}
