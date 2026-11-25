package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.enums.ClientPaymentTerms;
import com.sanigest.catalog.domain.enums.ClientStatus;
import com.sanigest.catalog.domain.enums.ClientType;
import com.sanigest.catalog.domain.exception.InvalidClientPatch;
import com.sanigest.catalog.domain.model.ClientPatch;
import com.sanigest.catalog.domain.model.ClientPatch.Value;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;

public final class ClientPatchRequest {
  private static final Set<String> FIELDS =
      Set.of(
          "name",
          "taxId",
          "email",
          "phone",
          "address",
          "clientType",
          "alternatePhone",
          "countryCode",
          "provinceCode",
          "municipalityCode",
          "preferredReceiptType",
          "paymentTerms",
          "categoryId",
          "creditLimit",
          "observations");
  private static final Set<String> LEGACY_FIELDS =
      Set.of("name", "taxId", "email", "phone", "address", "id", "companyId", "userId", "status");

  private ClientPatchRequest() {}

  public static ClientPatch details(JsonNode body) {
    if (!body.isObject() || body.isEmpty() || !FIELDS.containsAll(body.propertyNames())) {
      throw new InvalidClientPatch();
    }
    return parse(body);
  }

  static ClientPatch creation(JsonNode body) {
    if (!body.isObject()) {
      throw new InvalidClientPatch();
    }
    var patch = parse(body);
    if (!(patch.hasProfileFields() ? FIELDS : LEGACY_FIELDS).containsAll(body.propertyNames())) {
      throw new InvalidClientPatch();
    }
    return patch;
  }

  private static ClientPatch parse(JsonNode body) {
    try {
      return new ClientPatch(
          read(body, "name", ClientPatchRequest::text),
          read(body, "taxId", ClientPatchRequest::text),
          read(body, "email", ClientPatchRequest::text),
          read(body, "phone", ClientPatchRequest::text),
          read(body, "address", ClientPatchRequest::text),
          read(body, "clientType", node -> ClientType.valueOf(text(node))),
          read(body, "alternatePhone", ClientPatchRequest::text),
          read(body, "countryCode", ClientPatchRequest::text),
          read(body, "provinceCode", ClientPatchRequest::text),
          read(body, "municipalityCode", ClientPatchRequest::text),
          read(body, "preferredReceiptType", ClientPatchRequest::text),
          read(body, "paymentTerms", node -> ClientPaymentTerms.valueOf(text(node))),
          read(body, "categoryId", ClientPatchRequest::uuid),
          read(body, "creditLimit", ClientPatchRequest::money),
          read(body, "observations", ClientPatchRequest::text));
    } catch (IllegalArgumentException invalid) {
      throw new InvalidClientPatch();
    }
  }

  private static <T> Value<T> read(JsonNode body, String key, Function<JsonNode, T> convert) {
    var node = body.get(key);
    return node == null ? null : new Value<>(node.isNull() ? null : convert.apply(node));
  }

  public static ClientStatus status(JsonNode body) {
    if (!body.isObject() || body.size() != 1 || !body.has("status")) {
      throw new InvalidClientPatch();
    }
    try {
      return ClientStatus.valueOf(text(body.get("status")));
    } catch (IllegalArgumentException invalid) {
      throw new InvalidClientPatch();
    }
  }

  private static BigDecimal money(JsonNode node) {
    String amount = text(node);
    if (!amount.matches("[0-9]{1,17}([.][0-9]{1,2})?")) {
      throw new InvalidClientPatch();
    }
    return new BigDecimal(amount);
  }

  private static UUID uuid(JsonNode node) {
    String value = text(node);
    var id = UUID.fromString(value);
    if (!id.toString().equalsIgnoreCase(value)) {
      throw new InvalidClientPatch();
    }
    return id;
  }

  private static String text(JsonNode node) {
    if (!node.isString()) {
      throw new InvalidClientPatch();
    }
    return node.asString();
  }
}
