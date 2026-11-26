package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.exception.InvalidClientPatch;
import com.sanigest.catalog.domain.model.ClientDetails;
import com.sanigest.catalog.domain.model.ClientPatch.Value;
import com.sanigest.catalog.domain.model.ClientProfile;
import jakarta.validation.Validator;
import tools.jackson.databind.JsonNode;

public record CreateClientRequest(ClientDetails details) {
  public static CreateClientRequest from(JsonNode body, Validator validator) {
    var fields = ClientPatchRequest.creation(body);
    fields.validateFields(validator);
    String name = value(fields.name());
    if (name == null || name.isBlank()) {
      throw new InvalidClientPatch();
    }
    if (value(fields.clientType()) == null
        || explicitNull(fields.countryCode())
        || explicitNull(fields.paymentTerms())
        || explicitNull(fields.categoryId())) {
      throw new InvalidClientPatch();
    }
    var profile =
        new ClientProfile(
            value(fields.clientType()),
            value(fields.alternatePhone()),
            fields.countryCode() == null ? "DO" : value(fields.countryCode()),
            value(fields.provinceCode()),
            value(fields.municipalityCode()),
            value(fields.preferredReceiptType()),
            value(fields.paymentTerms()),
            value(fields.categoryId()),
            value(fields.creditLimit()),
            value(fields.observations()));
    return new CreateClientRequest(
        new ClientDetails(
            name,
            value(fields.taxId()),
            value(fields.email()),
            value(fields.phone()),
            value(fields.address()),
            profile));
  }

  private static boolean explicitNull(Value<?> field) {
    return field != null && field.value() == null;
  }

  private static <T> T value(Value<T> field) {
    return field == null ? null : field.value();
  }
}
