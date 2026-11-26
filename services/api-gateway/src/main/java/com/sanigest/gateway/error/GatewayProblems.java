package com.sanigest.gateway.error;

import com.sanigest.gateway.observability.TraceHeaders;
import com.sanigest.security.http.HttpProblems;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GatewayProblems {
  private final ObjectMapper json;

  public GatewayProblems(JsonMapper json) {
    this.json =
        json.rebuild().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
  }

  public Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status, String code) {
    return write(exchange, status, code, HttpProblems.detail(status), null);
  }

  public Mono<Void> write(
      ServerWebExchange exchange, HttpStatusCode status, String code, String detail, String field) {
    var response = exchange.getResponse();
    if (!response.setStatusCode(status)) {
      return Mono.empty();
    }
    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    response.getHeaders().remove(org.springframework.http.HttpHeaders.CONTENT_LENGTH);
    if (exchange.getRequest().getMethod() == HttpMethod.HEAD) {
      return response.setComplete();
    }
    var problem =
        HttpProblems.create(
            status,
            code,
            detail,
            exchange.getAttributeOrDefault(
                TraceHeaders.TRACE_ID,
                exchange.getRequest().getHeaders().getFirst(TraceHeaders.TRACE_ID)));
    if (field != null) {
      problem.setProperty("errors", java.util.Map.of(field, detail));
    }
    return response.writeWith(
        Mono.fromSupplier(() -> response.bufferFactory().wrap(json.writeValueAsBytes(problem))));
  }
}
