package com.sanigest.identity.api.controller.account;

import com.sanigest.identity.application.usecase.account.CheckAdvisorEligibilityUseCase;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "test"})
final class AdvisorEligibilityController {
  private final CheckAdvisorEligibilityUseCase eligibility;

  AdvisorEligibilityController(CheckAdvisorEligibilityUseCase eligibility) {
    this.eligibility = eligibility;
  }

  @GetMapping("/internal/v1/accounts/{userId}/advisor-eligibility")
  EligibilityResponse check(@PathVariable String userId) {
    return new EligibilityResponse(eligibility.isEligible(parseUserId(userId)));
  }

  private static UUID parseUserId(String userId) {
    try {
      UUID parsed = UUID.fromString(userId);
      if (parsed.toString().equalsIgnoreCase(userId)) {
        return parsed;
      }
    } catch (IllegalArgumentException invalidUuid) {
      // Use a fixed error message for both malformed and non-canonical IDs.
    }
    throw new InvalidUserId();
  }

  @ExceptionHandler(InvalidUserId.class)
  ProblemDetail invalidUserId() {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "userId must be a UUID");
    problem.setProperty("errors", Map.of("userId", "Invalid UUID"));
    return problem;
  }

  private static final class InvalidUserId extends IllegalArgumentException {}

  @ExceptionHandler({DataAccessException.class, TransactionException.class})
  ProblemDetail unavailable() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE, "Advisor eligibility is temporarily unavailable");
  }

  record EligibilityResponse(boolean eligible) {}
}
