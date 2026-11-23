package com.sanigest.catalog.api.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

final class CatalogItemStatusDeserializer extends ValueDeserializer<CatalogItemStatusRequest> {
  @Override
  public CatalogItemStatusRequest deserialize(JsonParser parser, DeserializationContext context) {
    if (!parser.hasToken(JsonToken.START_OBJECT)
        || parser.nextToken() != JsonToken.PROPERTY_NAME
        || !"active".equals(parser.currentName())) {
      return invalid(context);
    }
    var value = parser.nextToken();
    if ((value != JsonToken.VALUE_TRUE && value != JsonToken.VALUE_FALSE)
        || parser.nextToken() != JsonToken.END_OBJECT) {
      return invalid(context);
    }
    return new CatalogItemStatusRequest(value == JsonToken.VALUE_TRUE);
  }

  private static CatalogItemStatusRequest invalid(DeserializationContext context) {
    return context.reportInputMismatch(
        CatalogItemStatusRequest.class, "Invalid catalog item status");
  }
}
