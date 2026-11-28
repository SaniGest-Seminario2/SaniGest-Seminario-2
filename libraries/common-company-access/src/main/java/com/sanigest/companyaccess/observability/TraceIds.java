package com.sanigest.companyaccess.observability;

import java.util.UUID;
import java.util.regex.Pattern;

public final class TraceIds {
  private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");

  private TraceIds() {}

  public static String normalize(String value) {
    if (value != null && TRACE_ID.matcher(value).matches() && !value.equals("0".repeat(32))) {
      return value;
    }
    return UUID.randomUUID().toString().replace("-", "");
  }
}
