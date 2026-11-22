package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.Client;
import com.sanigest.catalog.domain.ClientPatch;
import com.sanigest.catalog.domain.ClientPatch.Field;
import com.sanigest.catalog.domain.InvalidClientPatch;
import java.util.EnumMap;
import tools.jackson.databind.JsonNode;

public final class ClientPatchRequest {
  private ClientPatchRequest() {}

  public static ClientPatch details(JsonNode body) {
    if (!body.isObject()) {
      throw new InvalidClientPatch();
    }
    var values = new EnumMap<Field, String>(Field.class);
    for (var field : Field.values()) {
      var node = body.get(field.property());
      if (node != null) {
        values.put(field, node.isNull() ? null : text(node));
      }
    }
    if (values.size() != body.size()) {
      throw new InvalidClientPatch();
    }
    return new ClientPatch(values);
  }

  public static Client.Status status(JsonNode body) {
    if (!body.isObject() || body.size() != 1 || !body.has("status")) {
      throw new InvalidClientPatch();
    }
    try {
      return Client.Status.valueOf(text(body.get("status")));
    } catch (IllegalArgumentException invalid) {
      throw new InvalidClientPatch();
    }
  }

  private static String text(JsonNode node) {
    if (!node.isString()) {
      throw new InvalidClientPatch();
    }
    return node.asString();
  }
}
