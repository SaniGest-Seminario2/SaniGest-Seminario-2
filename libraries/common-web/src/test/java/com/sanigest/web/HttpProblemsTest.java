package com.sanigest.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;
import tools.jackson.databind.json.JsonMapper;

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
    "504, UPSTREAM_TIMEOUT",
    "418, HTTP_418",
    "499, HTTP_499"
  })
  void producesSafeProblemForEachStatus(int value, String code) {
    var status = HttpStatusCode.valueOf(value);
    var problem =
        HttpProblems.create(
            status, HttpProblems.code(status), HttpProblems.detail(status), "untrusted");
    assertThat(problem.getStatus()).isEqualTo(value);
    assertThat(problem.getType().getScheme()).isEqualTo("urn");
    if (HttpStatus.resolve(value) != null) {
      assertThat(problem.getTitle()).isNotBlank();
    }
    assertThat(problem.getDetail()).isNotBlank();
    var fields = problem.getProperties();
    assertThat(fields).containsEntry("errorCode", code);
    assertThat(fields.get("traceId").toString()).matches("[0-9a-f]{32}");
    assertThat(Instant.parse(fields.get("timestamp").toString())).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  void serializesRfc9457FieldsAndExtensionsAtTheTopLevel() throws IOException {
    var trace = "0123456789abcdef0123456789abcdef";
    var problem =
        HttpProblems.create(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid page", trace);
    problem.setInstance(URI.create("/items"));
    var output = new MockHttpOutputMessage();

    new JacksonJsonHttpMessageConverter()
        .write(problem, MediaType.APPLICATION_PROBLEM_JSON, output);

    var json = JsonMapper.shared().readTree(output.getBodyAsBytes());
    assertThat(json.propertyNames())
        .containsExactlyInAnyOrder(
            "type", "title", "status", "detail", "instance", "errorCode", "timestamp", "traceId");
    assertThat(json.path("status").asInt()).isEqualTo(400);
    assertThat(json.path("type").asString()).isEqualTo("urn:sanigest:problem:invalid_request");
    assertThat(json.path("traceId").asString()).isEqualTo(trace);
  }

  @Test
  void serializesPaginationWithGenericContentAndLongTotals() {
    var page =
        new PageResponse<>(List.of(new Item("example")), 1, 2, 6_000_000_000L, 3_000_000_000L);

    var json = JsonMapper.shared().readTree(JsonMapper.shared().writeValueAsBytes(page));

    assertThat(json)
        .isEqualTo(
            JsonMapper.shared()
                .readTree(
                    """
                    {"content":[{"name":"example"}],"page":1,"size":2,
                     "totalElements":6000000000,"totalPages":3000000000}
                    """));
  }

  record Item(String name) {}
}
