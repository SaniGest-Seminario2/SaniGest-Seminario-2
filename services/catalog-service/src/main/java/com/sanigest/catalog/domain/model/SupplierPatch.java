package com.sanigest.catalog.domain.model;

import com.sanigest.catalog.domain.enums.SupplierPaymentTerms;
import com.sanigest.catalog.domain.enums.SupplierSupplyType;
import com.sanigest.catalog.domain.exception.InvalidSupplierPatch;
import java.util.Map;
import java.util.UUID;

public record SupplierPatch(
    Value<String> name,
    Value<String> taxId,
    Value<String> contact,
    Value<String> phone,
    Value<String> email,
    Value<String> address,
    Value<SupplierSupplyType> supplyType,
    Value<SupplierPaymentTerms> paymentTerms,
    Value<UUID> categoryId,
    Value<Integer> creditDays,
    Value<String> observations) {

  public record Value<T>(T value) {
    static <T> T resolve(Value<T> update, T previous) {
      return update == null ? previous : update.value();
    }
  }

  public SupplierPatch(Map<Field, String> values) {
    this(
        legacy(values, Field.NAME),
        legacy(values, Field.TAX_ID),
        legacy(values, Field.CONTACT),
        legacy(values, Field.PHONE),
        legacy(values, Field.EMAIL),
        null,
        null,
        null,
        null,
        null,
        null);
    if (values.isEmpty()) {
      throw new InvalidSupplierPatch();
    }
  }

  private static Value<String> legacy(Map<Field, String> values, Field field) {
    return values.containsKey(field) ? new Value<>(values.get(field)) : null;
  }

  public enum Field {
    NAME("name"),
    TAX_ID("taxId"),
    CONTACT("contact"),
    PHONE("phone"),
    EMAIL("email");

    private final String property;

    Field(String property) {
      this.property = property;
    }

    public String property() {
      return property;
    }
  }

  public SupplierDetails createDetails() {
    var profile =
        new SupplierProfile(
            Value.resolve(address, null),
            Value.resolve(supplyType, null),
            Value.resolve(paymentTerms, SupplierPaymentTerms.CASH),
            Value.resolve(categoryId, null),
            Value.resolve(creditDays, null),
            Value.resolve(observations, null));
    if (profile.paymentTerms() == null) {
      throw new InvalidSupplierPatch();
    }
    return new SupplierDetails(
        Value.resolve(name, null),
        Value.resolve(taxId, null),
        Value.resolve(contact, null),
        Value.resolve(phone, null),
        Value.resolve(email, null),
        profile);
  }

  SupplierDetails applyTo(SupplierDetails current) {
    var previous = current.profile();
    if (paymentTerms != null && paymentTerms.value() == null) {
      throw new InvalidSupplierPatch();
    }
    var profile =
        new SupplierProfile(
            Value.resolve(address, previous.address()),
            Value.resolve(supplyType, previous.supplyType()),
            Value.resolve(paymentTerms, previous.paymentTerms()),
            Value.resolve(categoryId, previous.categoryId()),
            Value.resolve(creditDays, previous.creditDays()),
            Value.resolve(observations, previous.observations()));
    return new SupplierDetails(
        Value.resolve(name, current.name()),
        taxId == null
            ? current.taxId()
            : SupplierProfile.normalizeTaxId(taxId.value(), !profile.equals(SupplierProfile.EMPTY)),
        Value.resolve(contact, current.contact()),
        Value.resolve(phone, current.phone()),
        Value.resolve(email, current.email()),
        profile);
  }
}
