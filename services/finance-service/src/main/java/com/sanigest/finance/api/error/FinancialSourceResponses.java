package com.sanigest.finance.api.error;

import com.sanigest.finance.application.exception.FinancialSourceFailure;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class FinancialSourceResponses {
  private FinancialSourceResponses() {}

  public static <T> T execute(Supplier<T> action) {
    try {
      return action.get();
    } catch (FinancialSourceFailure failure) {
      var status =
          switch (failure.kind()) {
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
          };
      throw new ResponseStatusException(status, failure.getMessage());
    }
  }
}
