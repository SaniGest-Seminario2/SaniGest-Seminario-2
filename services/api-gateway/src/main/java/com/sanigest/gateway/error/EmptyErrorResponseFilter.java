package com.sanigest.gateway.error;

import com.sanigest.security.http.HttpProblems;
import org.reactivestreams.Publisher;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Order(-1000)
class EmptyErrorResponseFilter implements WebFilter {
  private final GatewayProblems problems;

  EmptyErrorResponseFilter(GatewayProblems problems) {
    this.problems = problems;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (exchange.getRequest().getMethod() == HttpMethod.HEAD) {
      return chain.filter(exchange);
    }
    var response =
        new ServerHttpResponseDecorator(exchange.getResponse()) {
          @Override
          public Mono<Void> setComplete() {
            var status = getStatusCode();
            return status != null && status.isError() && !isCommitted()
                ? problems.write(exchange, status, HttpProblems.code(status))
                : super.setComplete();
          }

          @Override
          public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            var status = getStatusCode();
            if (status == null || !status.isError() || isCommitted()) {
              return super.writeWith(body);
            }
            return Flux.from(body)
                .switchOnFirst((signal, buffers) ->
                    signal.isOnComplete() ? setComplete() : super.writeWith(buffers))
                .then();
          }
        };
    return chain.filter(exchange.mutate().response(response).build());
  }
}
