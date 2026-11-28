package com.sanigest.catalog.api.dto.supplier;

import com.sanigest.catalog.application.usecase.supplier.ReplaceSupplierItems;
import com.sanigest.catalog.domain.exception.supplier.InvalidSupplierPatch;
import com.sanigest.catalog.domain.model.supplier.SupplierItemOffer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public record SupplierItemsRequest(List<SupplierItemOffer> items, boolean replacePrimary) {
  private static final Set<String> REQUEST_FIELDS = Set.of("items", "replacePrimary");
  private static final Set<String> OFFER_FIELDS =
      Set.of("catalogItemId", "offeredCost", "supplierReference", "primary");

  public static SupplierItemsRequest parse(JsonNode body) {
    if (!body.isObject() || !REQUEST_FIELDS.containsAll(body.propertyNames())) {
      throw new InvalidSupplierPatch();
    }
    return new SupplierItemsRequest(
        parseOffers(body.get("items"), false), flag(body.get("replacePrimary")));
  }

  static List<SupplierItemOffer> parseOffers(JsonNode entries, boolean allowNumericCost) {
    if (entries == null || !entries.isArray() || entries.size() > ReplaceSupplierItems.MAX_ITEMS) {
      throw new InvalidSupplierPatch();
    }
    var items = new ArrayList<SupplierItemOffer>(entries.size());
    for (var entry : entries) {
      items.add(offer(entry, allowNumericCost));
    }
    return List.copyOf(items);
  }

  private static SupplierItemOffer offer(JsonNode entry, boolean allowNumericCost) {
    if (!entry.isObject() || !OFFER_FIELDS.containsAll(entry.propertyNames())) {
      throw new InvalidSupplierPatch();
    }
    try {
      return new SupplierItemOffer(
          SupplierPatchRequest.uuid(entry.path("catalogItemId")),
          cost(entry.get("offeredCost"), allowNumericCost),
          text(entry.get("supplierReference")),
          flag(entry.get("primary")));
    } catch (IllegalArgumentException invalid) {
      throw new InvalidSupplierPatch();
    }
  }

  private static BigDecimal cost(JsonNode node, boolean allowNumericCost) {
    if (allowNumericCost && node != null && node.isNumber()) {
      return node.decimalValue();
    }
    var value = text(node);
    if (value != null && !value.matches("[0-9]{1,15}([.][0-9]{1,4})?")) {
      throw new InvalidSupplierPatch();
    }
    return value == null ? null : new BigDecimal(value);
  }

  private static String text(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (!node.isString()) {
      throw new InvalidSupplierPatch();
    }
    return node.asString();
  }

  private static boolean flag(JsonNode node) {
    if (node == null) {
      return false;
    }
    if (!node.isBoolean()) {
      throw new InvalidSupplierPatch();
    }
    return node.booleanValue();
  }
}
