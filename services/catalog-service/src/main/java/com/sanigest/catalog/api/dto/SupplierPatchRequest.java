package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.exception.InvalidSupplierPatch;
import com.sanigest.catalog.domain.model.SupplierPatch;
import com.sanigest.catalog.domain.model.SupplierPatch.Field;
import java.util.EnumMap;
import tools.jackson.databind.JsonNode;

public final class SupplierPatchRequest {
  private SupplierPatchRequest() {}

  public static SupplierPatch details(JsonNode body) {
    if (!body.isObject()) {
      throw new InvalidSupplierPatch();
    }
    var values = new EnumMap<Field, String>(Field.class);
    for (var field : Field.values()) {
      var node = body.get(field.property());
      if (node != null) {
        values.put(field, node.isNull() ? null : text(node));
      }
    }
    if (values.size() != body.size()) {
      throw new InvalidSupplierPatch();
    }
    return new SupplierPatch(values);
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
