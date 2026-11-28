package com.sanigest.catalog.domain.model.supplier;

import com.sanigest.catalog.domain.exception.supplier.InvalidSupplierDetails;
import java.util.regex.Pattern;

public record SupplierDetails(
    String name,
    String taxId,
    String contact,
    String phone,
    String email,
    SupplierProfile profile) {
  public static final int NAME_LIMIT = 160;
  public static final int CONTACT_LIMIT = 160;
  public static final int TAX_ID_LIMIT = 64;
  public static final int EMAIL_LIMIT = 320;
  public static final int PHONE_LIMIT = 32;
  public static final String PHONE_FORMAT = "\\+?(?=(?:[^0-9]*[0-9]){7})[0-9() .-]{7,32}";
  public static final String EMAIL_FORMAT = "[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+";

  private static final Pattern PHONE = Pattern.compile(PHONE_FORMAT);
  private static final Pattern EMAIL = Pattern.compile(EMAIL_FORMAT);

  public SupplierDetails(String name, String taxId, String contact, String phone, String email) {
    this(name, taxId, contact, phone, email, SupplierProfile.EMPTY);
  }

  public SupplierDetails {
    java.util.Objects.requireNonNull(profile);
    name = requiredText(name, NAME_LIMIT, "name");
    contact = requiredText(contact, CONTACT_LIMIT, "contact");
    if (taxId != null) {
      if (taxId.length() > TAX_ID_LIMIT || taxId.indexOf(0) >= 0) {
        throw new InvalidSupplierDetails("taxId");
      }
      taxId = taxId.strip();
      if (taxId.isEmpty()) {
        taxId = null;
      }
    }
    if (phone != null && (phone.length() > PHONE_LIMIT || !PHONE.matcher(phone).matches())) {
      throw new InvalidSupplierDetails("phone");
    }
    if (email != null
        && (email.length() > EMAIL_LIMIT
            || email.indexOf(0) >= 0
            || !EMAIL.matcher(email).matches())) {
      throw new InvalidSupplierDetails("email");
    }
  }

  private static String requiredText(String value, int limit, String field) {
    if (value == null || value.isBlank() || value.length() > limit || value.indexOf(0) >= 0) {
      throw new InvalidSupplierDetails(field);
    }
    return value.strip();
  }
}
