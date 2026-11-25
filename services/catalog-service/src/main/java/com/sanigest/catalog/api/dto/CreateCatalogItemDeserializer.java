package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.enums.CatalogItemType;
import com.sanigest.catalog.domain.model.CatalogItemProfile;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.UUID;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

final class CreateCatalogItemDeserializer extends ValueDeserializer<CreateCatalogItemRequest> {
  private static final java.util.Set<String> LEGACY_FIELDS =
      java.util.Set.of("name", "type", "price");

  @Override
  public CreateCatalogItemRequest deserialize(JsonParser parser, DeserializationContext context) {
    if (!parser.hasToken(JsonToken.START_OBJECT)) {
      return invalid(context);
    }
    var input = new Fields();
    var fields = new HashSet<String>();
    try {
      while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
        var field = parser.currentName();
        if (!fields.add(field)) {
          return invalid(context);
        }
        parser.nextToken();
        input.read(field, parser);
      }
    } catch (IllegalArgumentException invalidValue) {
      return invalid(context);
    }
    if (!parser.hasToken(JsonToken.END_OBJECT)) {
      return invalid(context);
    }
    return request(input, fields, context);
  }

  private static CreateCatalogItemRequest request(
      Fields input, java.util.Set<String> fields, DeserializationContext context) {
    boolean expanded =
        fields.stream().anyMatch(field -> !LEGACY_FIELDS.contains(field)) || input.textPrice;
    if (expanded && input.taxClassificationCode == null) {
      return invalid(context);
    }
    return new CreateCatalogItemRequest(
        input.name,
        input.type,
        input.price,
        input.description,
        expanded
            ? new CatalogItemProfile(
                input.sku,
                input.barcode,
                input.categoryId,
                input.unitCode,
                input.estimatedCost,
                input.taxClassificationCode,
                input.availableForSale == null || input.availableForSale,
                Boolean.TRUE.equals(input.availableForPurchase))
            : CatalogItemProfile.LEGACY);
  }

  private static CreateCatalogItemRequest invalid(DeserializationContext context) {
    return context.reportInputMismatch(
        CreateCatalogItemRequest.class, "Invalid catalog item payload");
  }

  private static final class Fields {
    String name = null;
    CatalogItemType type = null;
    BigDecimal price = null;
    String description = null;
    String sku = null;
    String barcode = null;
    UUID categoryId = null;
    String unitCode = null;
    BigDecimal estimatedCost = null;
    String taxClassificationCode = null;
    Boolean availableForSale = null;
    Boolean availableForPurchase = null;
    boolean textPrice = false;

    private void read(String field, JsonParser parser) {
      switch (field) {
        case "name" -> name = CatalogItemJson.text(parser);
        case "type" -> type = CatalogItemType.valueOf(CatalogItemJson.text(parser));
        case "price" -> {
          textPrice = parser.hasToken(JsonToken.VALUE_STRING);
          price = CatalogItemJson.decimal(parser);
        }
        case "description" -> description = CatalogItemJson.nullable(parser, CatalogItemJson::text);
        case "sku" -> sku = CatalogItemJson.nullable(parser, CatalogItemJson::text);
        case "barcode" -> barcode = CatalogItemJson.nullable(parser, CatalogItemJson::text);
        default -> readCommercial(field, parser);
      }
    }

    private void readCommercial(String field, JsonParser parser) {
      switch (field) {
        case "categoryId" -> categoryId = CatalogItemJson.nullable(parser, CatalogItemJson::id);
        case "unitCode" -> unitCode = CatalogItemJson.nullable(parser, CatalogItemJson::text);
        case "estimatedCost" ->
            estimatedCost = CatalogItemJson.nullable(parser, CatalogItemJson::decimal);
        case "taxClassificationCode" ->
            taxClassificationCode = CatalogItemJson.nullable(parser, CatalogItemJson::text);
        case "availableForSale" -> availableForSale = CatalogItemJson.flag(parser);
        case "availableForPurchase" -> availableForPurchase = CatalogItemJson.flag(parser);
        default -> throw new IllegalArgumentException();
      }
    }
  }
}
