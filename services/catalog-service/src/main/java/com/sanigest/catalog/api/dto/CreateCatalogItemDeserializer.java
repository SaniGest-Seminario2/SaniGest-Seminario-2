package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.enums.CatalogItemType;
import java.math.BigDecimal;
import java.util.HashSet;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

final class CreateCatalogItemDeserializer extends ValueDeserializer<CreateCatalogItemRequest> {
  @Override
  public CreateCatalogItemRequest deserialize(JsonParser parser, DeserializationContext context) {
    if (!parser.hasToken(JsonToken.START_OBJECT)) {
      return invalid(context);
    }
    String name = null;
    CatalogItemType type = null;
    BigDecimal price = null;
    var fields = new HashSet<String>();
    while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
      var field = parser.currentName();
      if (!fields.add(field)) {
        return invalid(context);
      }
      var value = parser.nextToken();
      switch (field) {
        case "name" -> {
          if (value != JsonToken.VALUE_STRING) {
            return invalid(context);
          }
          name = parser.getString();
        }
        case "type" -> {
          if (value != JsonToken.VALUE_STRING) {
            return invalid(context);
          }
          try {
            type = CatalogItemType.valueOf(parser.getString());
          } catch (IllegalArgumentException invalidType) {
            return invalid(context);
          }
        }
        case "price" -> {
          if (value != JsonToken.VALUE_NUMBER_INT && value != JsonToken.VALUE_NUMBER_FLOAT) {
            return invalid(context);
          }
          price = parser.getDecimalValue();
        }
        default -> {
          return invalid(context);
        }
      }
    }
    if (!parser.hasToken(JsonToken.END_OBJECT)) {
      return invalid(context);
    }
    return new CreateCatalogItemRequest(name, type, price);
  }

  private static CreateCatalogItemRequest invalid(DeserializationContext context) {
    return context.reportInputMismatch(
        CreateCatalogItemRequest.class, "Invalid catalog item payload");
  }
}
