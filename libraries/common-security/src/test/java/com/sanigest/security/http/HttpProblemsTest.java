package com.sanigest.security.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

class HttpProblemsTest {
  @ParameterizedTest
  @CsvSource({
    "400, INVALID_REQUEST",
    "401, AUTHENTICATION_REQUIRED",
    "403, FORBIDDEN",
    "404, RESOURCE_NOT_FOUND",
    "405, METHOD_NOT_ALLOWED",
    "409, CONFLICT",
    "415, UNSUPPORTED_MEDIA_TYPE",
    "500, INTERNAL_ERROR",
    "503, DEPENDENCY_UNAVAILABLE",
    "504, UPSTREAM_TIMEOUT"
  })
  void producesSafeProblemForEachStatus(int value, String code) {
    var status = HttpStatus.valueOf(value);
    var problem =
        HttpProblems.create(
            status, HttpProblems.code(status), HttpProblems.detail(status), "untrusted");
    assertThat(problem.getStatus()).isEqualTo(value);
    assertThat(problem.getType().getScheme()).isEqualTo("urn");
    assertThat(problem.getTitle()).isNotBlank();
    assertThat(problem.getDetail()).isNotBlank();
    var fields = problem.getProperties();
    assertThat(fields).containsEntry("errorCode", code);
    assertThat(fields.get("traceId").toString()).matches("[0-9a-f]{32}");
    assertThat(Instant.parse(fields.get("timestamp").toString())).isBeforeOrEqualTo(Instant.now());
  }
}
