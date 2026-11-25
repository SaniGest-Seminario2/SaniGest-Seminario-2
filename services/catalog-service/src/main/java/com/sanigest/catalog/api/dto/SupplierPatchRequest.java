package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.enums.SupplierPaymentTerms;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.enums.SupplierSupplyType;
import com.sanigest.catalog.domain.exception.InvalidSupplierPatch;
import com.sanigest.catalog.domain.model.SupplierPatch;
import com.sanigest.catalog.domain.model.SupplierPatch.Value;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;

public final class SupplierPatchRequest {
  private SupplierPatchRequest() {}

  private static final Set<String> FIELDS =
      Set.of(
          "name",
          "taxId",
          "contact",
          "phone",
          "email",
          "address",
          "supplyType",
          "paymentTerms",
          "categoryId",
          "creditDays",
          "observations");

  public static SupplierPatch details(JsonNode body) {
    return parse(body, false);
  }

  static SupplierPatch creation(JsonNode body) {
    return parse(body, true);
  }

  private static SupplierPatch parse(JsonNode body, boolean creation) {
    if (!body.isObject()
        || body.isEmpty()
        || !body.propertyNames().stream()
            .allMatch(key ->
                FIELDS.contains(key)
                    || (creation && Set.of("id", "companyId", "status", "userId").contains(key)))) {
      throw new InvalidSupplierPatch();
    }
    try {
      var patch =
          new SupplierPatch(
              read(body, "name", SupplierPatchRequest::text),
              read(body, "taxId", SupplierPatchRequest::text),
              read(body, "contact", SupplierPatchRequest::text),
              read(body, "phone", SupplierPatchRequest::text),
              read(body, "email", SupplierPatchRequest::text),
              read(body, "address", SupplierPatchRequest::text),
              read(body, "supplyType", node -> SupplierSupplyType.valueOf(text(node))),
              read(body, "paymentTerms", node -> SupplierPaymentTerms.valueOf(text(node))),
              read(body, "categoryId", SupplierPatchRequest::uuid),
              read(body, "creditDays", SupplierPatchRequest::integer),
              read(body, "observations", SupplierPatchRequest::text));
      if (creation && patch.hasProfileFields() && !FIELDS.containsAll(body.propertyNames())) {
        throw new InvalidSupplierPatch();
      }
      return patch;
    } catch (IllegalArgumentException invalid) {
      throw new InvalidSupplierPatch();
    }
  }

  private static <T> Value<T> read(JsonNode body, String key, Function<JsonNode, T> convert) {
    var node = body.get(key);
    return node == null ? null : new Value<>(node.isNull() ? null : convert.apply(node));
  }

  private static Integer integer(JsonNode node) {
    if (!node.isIntegralNumber() || !node.canConvertToInt()) {
      throw new InvalidSupplierPatch();
    }
    return node.intValue();
  }

  public static UUID uuid(JsonNode node) {
    String value = text(node);
    var id = UUID.fromString(value);
    if (!id.toString().equalsIgnoreCase(value)) {
      throw new InvalidSupplierPatch();
    }
    return id;
  }

  public static SupplierStatus status(JsonNode body) {
    if (!body.isObject() || body.size() != 1 || !body.has("status")) {
      throw new InvalidSupplierPatch();
    }
    try {
      return SupplierStatus.valueOf(text(body.get("status")));
    } catch (IllegalArgumentException invalid) {
      throw new InvalidSupplierPatch();
    }
  }

  private static String text(JsonNode node) {
    if (!node.isString()) {
      throw new InvalidSupplierPatch();
    }
    return node.asString();
  }
}
