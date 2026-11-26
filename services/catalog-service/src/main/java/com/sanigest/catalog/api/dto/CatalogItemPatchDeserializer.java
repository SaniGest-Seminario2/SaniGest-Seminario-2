package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.model.CatalogItemPatch;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
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
    if (!parser.hasToken(JsonToken.END_OBJECT) || fields.isEmpty()) {
      return invalid(context);
    }
    return new CatalogItemPatchRequest(
        new CatalogItemPatch(
            input.name,
            input.description,
            input.price,
            input.sku,
            input.barcode,
            input.categoryId,
            input.unitCode,
            input.estimatedCost,
            input.taxClassificationCode,
            input.availableForSale,
            input.availableForPurchase));
  }

  private static CatalogItemPatchRequest invalid(DeserializationContext context) {
    return context.reportInputMismatch(CatalogItemPatchRequest.class, "Invalid catalog item patch");
  }

  private static final class Fields {
    Optional<String> name = Optional.empty();
    Optional<BigDecimal> price = Optional.empty();
    Optional<Optional<String>> description = Optional.empty();
    Optional<Optional<String>> sku = Optional.empty();
    Optional<Optional<String>> barcode = Optional.empty();
    Optional<Optional<UUID>> categoryId = Optional.empty();
    Optional<Optional<String>> unitCode = Optional.empty();
    Optional<Optional<BigDecimal>> estimatedCost = Optional.empty();
    Optional<Optional<String>> taxClassificationCode = Optional.empty();
    Optional<Boolean> availableForSale = Optional.empty();
    Optional<Boolean> availableForPurchase = Optional.empty();

    private void read(String field, JsonParser parser) {
      switch (field) {
        case "name" -> name = Optional.of(CatalogItemJson.text(parser));
        case "price" -> price = Optional.of(CatalogItemJson.decimal(parser));
        case "description" ->
            description =
                Optional.of(
                    Optional.ofNullable(CatalogItemJson.nullable(parser, CatalogItemJson::text)));
        case "sku" ->
            sku =
                Optional.of(
                    Optional.ofNullable(CatalogItemJson.nullable(parser, CatalogItemJson::text)));
        case "barcode" ->
            barcode =
                Optional.of(
                    Optional.ofNullable(CatalogItemJson.nullable(parser, CatalogItemJson::text)));
        default -> readCommercial(field, parser);
      }
    }

    private void readCommercial(String field, JsonParser parser) {
      switch (field) {
        case "categoryId" ->
            categoryId =
                Optional.of(
                    Optional.ofNullable(CatalogItemJson.nullable(parser, CatalogItemJson::id)));
        case "unitCode" -> unitCode = Optional.of(Optional.of(CatalogItemJson.text(parser)));
        case "estimatedCost" ->
            estimatedCost =
                Optional.of(
                    Optional.ofNullable(
                        CatalogItemJson.nullable(parser, CatalogItemJson::decimal)));
        case "taxClassificationCode" ->
            taxClassificationCode = Optional.of(Optional.of(CatalogItemJson.text(parser)));
        case "availableForSale" -> availableForSale = Optional.of(CatalogItemJson.flag(parser));
        case "availableForPurchase" ->
            availableForPurchase = Optional.of(CatalogItemJson.flag(parser));
        default -> throw new IllegalArgumentException();
      }
    }
  }
}
