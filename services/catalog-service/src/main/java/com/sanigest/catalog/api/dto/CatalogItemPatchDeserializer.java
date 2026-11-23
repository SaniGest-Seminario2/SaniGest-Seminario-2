package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.model.CatalogItemPatch;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

final class CatalogItemPatchDeserializer extends ValueDeserializer<CatalogItemPatchRequest> {
  @Override
  public CatalogItemPatchRequest deserialize(JsonParser parser, DeserializationContext context) {
    if (!parser.hasToken(JsonToken.START_OBJECT)) {
      return invalid(context);
    }
    Optional<String> name = Optional.empty();
    Optional<Optional<String>> description = Optional.empty();
    Optional<BigDecimal> price = Optional.empty();
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
          name = Optional.of(parser.getString());
        }
        case "description" -> {
          if (value != JsonToken.VALUE_NULL && value != JsonToken.VALUE_STRING) {
            return invalid(context);
          }
          description =
              Optional.of(
                  value == JsonToken.VALUE_NULL
                      ? Optional.empty()
                      : Optional.of(parser.getString()));
        }
        case "price" -> {
          if (value != JsonToken.VALUE_NUMBER_INT && value != JsonToken.VALUE_NUMBER_FLOAT) {
            return invalid(context);
          }
          price = Optional.of(parser.getDecimalValue());
        }
        default -> {
          return invalid(context);
        }
      }
    }
    if (!parser.hasToken(JsonToken.END_OBJECT) || fields.isEmpty()) {
      return invalid(context);
    }
    return new CatalogItemPatchRequest(new CatalogItemPatch(name, description, price));
  }

  private static CatalogItemPatchRequest invalid(DeserializationContext context) {
    return context.reportInputMismatch(CatalogItemPatchRequest.class, "Invalid catalog item patch");
  }
}
