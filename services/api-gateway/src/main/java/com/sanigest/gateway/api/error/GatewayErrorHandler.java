package com.sanigest.gateway.api.error;

import com.sanigest.web.api.error.HttpProblems;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
class GatewayErrorHandler implements WebExceptionHandler {
  private final GatewayProblems problems;

  GatewayErrorHandler(GatewayProblems problems) {
    this.problems = problems;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
    if (exchange.getResponse().isCommitted()) {
      return Mono.error(failure);
    }
    var status = HttpStatus.INTERNAL_SERVER_ERROR;
    if (failure instanceof ErrorResponse error) {
      error
          .getHeaders()
          .forEach((name, values) -> exchange.getResponse().getHeaders().put(name, values));
      return problems.write(
          exchange, error.getStatusCode(), HttpProblems.code(error.getStatusCode()));
    }
    return problems.write(exchange, status, HttpProblems.code(status));
  }
}
