package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.application.dto.SupplierItemPage;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SupplierItemPageResponse<T>(
    List<T> content, int page, int size, long totalElements, long totalPages) {
  public static SupplierItemPageResponse<SuppliedItem> items(SupplierItemPage page) {
    return new SupplierItemPageResponse<>(
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

  public static SupplierItemPageResponse<ItemSupplier> suppliers(SupplierItemPage page) {
    return new SupplierItemPageResponse<>(
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
