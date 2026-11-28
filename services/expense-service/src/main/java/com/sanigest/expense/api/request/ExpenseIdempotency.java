package com.sanigest.expense.api.request;

import com.sanigest.expense.application.exception.ExpenseFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;

public final class ExpenseIdempotency {
  private ExpenseIdempotency() {}

  public static String key(HttpServletRequest http) {
    var keys = Collections.list(http.getHeaders("Idempotency-Key"));
    if (keys.size() != 1 || !keys.getFirst().matches("[A-Za-z0-9._:-]{1,128}")) {
      throw ExpenseFailure.invalid("EXPENSE_INVALID_IDEMPOTENCY_KEY");
    }
    return keys.getFirst();
  }
}
