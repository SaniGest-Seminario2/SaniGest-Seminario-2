package com.sanigest.identity.domain.model.account;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record EmailAddress(String value) {

  private static final int MAX_LENGTH = 254;
  private static final Pattern FORMAT =
      Pattern.compile(
          "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9]"
              + "(?:[A-Z0-9-]{0,61}[A-Z0-9])?"
              + "(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
          Pattern.CASE_INSENSITIVE);

  public EmailAddress {
    Objects.requireNonNull(value, "email must not be null");
    value = value.strip().toLowerCase(Locale.ROOT);
    if (value.isEmpty() || value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("email is invalid");
    }
  }

  public static EmailAddress of(String value) {
    return new EmailAddress(value);
  }
}
