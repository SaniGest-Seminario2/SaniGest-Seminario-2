package com.sanigest.catalog.domain.model.client;

import com.sanigest.catalog.domain.enums.client.ClientPaymentTerms;
import com.sanigest.catalog.domain.enums.client.ClientType;
import com.sanigest.catalog.domain.exception.client.InvalidClientPatch;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.UUID;

public record ClientPatch(
    Value<String> name,
    Value<String> taxId,
    Value<String> email,
    Value<String> phone,
    Value<String> address,
    Value<ClientType> clientType,
    Value<String> alternatePhone,
    Value<String> countryCode,
    Value<String> provinceCode,
    Value<String> municipalityCode,
    Value<String> preferredReceiptType,
    Value<ClientPaymentTerms> paymentTerms,
    Value<UUID> categoryId,
    Value<BigDecimal> creditLimit,
    Value<String> observations) {
  public record Value<T>(T value) {
    static <T> T orElse(Value<T> field, T current) {
      return field == null ? current : field.value();
    }
  }

  public boolean hasProfileFields() {
    return clientType != null
        || alternatePhone != null
        || countryCode != null
        || provinceCode != null
        || municipalityCode != null
        || preferredReceiptType != null
        || paymentTerms != null
        || categoryId != null
        || creditLimit != null
        || observations != null;
  }

  public void validateFields(Validator validator) {
    validate(validator, ClientDetails.class, "name", name);
    validate(validator, ClientDetails.class, "taxId", taxId);
    validate(validator, ClientDetails.class, "email", email);
    validate(validator, ClientDetails.class, "phone", phone);
    validate(validator, ClientDetails.class, "address", address);
    validate(validator, ClientProfile.class, "clientType", clientType);
    validate(validator, ClientProfile.class, "alternatePhone", alternatePhone);
    validate(validator, ClientProfile.class, "countryCode", countryCode);
    validate(validator, ClientProfile.class, "provinceCode", provinceCode);
    validate(validator, ClientProfile.class, "municipalityCode", municipalityCode);
    validate(validator, ClientProfile.class, "preferredReceiptType", preferredReceiptType);
    validate(validator, ClientProfile.class, "paymentTerms", paymentTerms);
    validate(validator, ClientProfile.class, "categoryId", categoryId);
    validate(validator, ClientProfile.class, "creditLimit", creditLimit);
    validate(validator, ClientProfile.class, "observations", observations);
  }

  private static void validate(Validator validator, Class<?> type, String name, Value<?> field) {
    if (field == null) {
      return;
    }
    if ((field.value() instanceof String text && text.indexOf(0) >= 0)
        || !validator.validateValue(type, name, field.value()).isEmpty()) {
      throw new InvalidClientPatch();
    }
  }

  ClientDetails applyTo(ClientDetails current) {
    String updatedName = Value.orElse(name, current.name());
    if (updatedName == null || updatedName.isBlank()) {
      throw new InvalidClientPatch();
    }
    var details =
        new ClientDetails(
            updatedName,
            Value.orElse(taxId, current.taxId()),
            Value.orElse(email, current.email()),
            Value.orElse(phone, current.phone()),
            Value.orElse(address, current.address()));
    if (current.profile() == null && !hasProfileFields()) {
      return details;
    }
    var profile =
        current.profile() == null
            ? new ClientProfile(null, null, null, null, null, null, null, null, null, null)
            : current.profile();
    var updated =
        new ClientProfile(
            Value.orElse(clientType, profile.clientType()),
            Value.orElse(alternatePhone, profile.alternatePhone()),
            Value.orElse(countryCode, profile.countryCode()),
            Value.orElse(provinceCode, profile.provinceCode()),
            Value.orElse(municipalityCode, profile.municipalityCode()),
            Value.orElse(preferredReceiptType, profile.preferredReceiptType()),
            Value.orElse(paymentTerms, profile.paymentTerms()),
            Value.orElse(categoryId, profile.categoryId()),
            Value.orElse(creditLimit, profile.creditLimit()),
            Value.orElse(observations, profile.observations()));
    updated.requireComplete();
    return details.withProfile(updated);
  }
}
