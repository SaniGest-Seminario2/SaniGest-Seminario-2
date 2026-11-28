package com.sanigest.catalog.api.dto.catalogitem;

import java.math.BigDecimal;
import java.util.UUID;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

final class CatalogItemJson {
  private static final java.util.regex.Pattern DECIMAL =
      java.util.regex.Pattern.compile("-?[0-9]{1,15}(\\.[0-9]{1,4})?");

  private CatalogItemJson() {}

  static <T> T nullable(JsonParser parser, java.util.function.Function<JsonParser, T> reader) {
    return parser.hasToken(JsonToken.VALUE_NULL) ? null : reader.apply(parser);
  }

  static String text(JsonParser parser) {
    if (!parser.hasToken(JsonToken.VALUE_STRING)) {
      throw new IllegalArgumentException();
    }
    return parser.getString();
  }

  static BigDecimal decimal(JsonParser parser) {
    if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)
        || parser.hasToken(JsonToken.VALUE_NUMBER_FLOAT)) {
      return parser.getDecimalValue();
    }
    var value = text(parser);
    if (!DECIMAL.matcher(value).matches()) {
      throw new IllegalArgumentException();
    }
    return new BigDecimal(value);
  }

  static UUID id(JsonParser parser) {
    var value = text(parser);
    var id = UUID.fromString(value);
    if (!id.toString().equalsIgnoreCase(value)) {
      throw new IllegalArgumentException();
    }
    return id;
  }

  static boolean flag(JsonParser parser) {
    if (!parser.hasToken(JsonToken.VALUE_TRUE) && !parser.hasToken(JsonToken.VALUE_FALSE)) {
      throw new IllegalArgumentException();
    }
    return parser.hasToken(JsonToken.VALUE_TRUE);
  }
}
