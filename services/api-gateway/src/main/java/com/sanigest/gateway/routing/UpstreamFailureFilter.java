package com.sanigest.gateway.routing;

import com.sanigest.gateway.error.GatewayProblems;
import java.net.ConnectException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

@Component
class UpstreamFailureFilter implements GlobalFilter, Ordered {
  private final GatewayProblems problems;

  UpstreamFailureFilter(GatewayProblems problems) {
    this.problems = problems;
  }

  @Override
  public int getOrder() {
    return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return chain
        .filter(exchange)
        .onErrorResume(error -> {
          if (exchange.getResponse().isCommitted()) {
            return Mono.error(error);
          }
          if (error instanceof ResponseStatusException status
              && status.getStatusCode().equals(HttpStatus.GATEWAY_TIMEOUT)) {
            return problems.write(exchange, HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT");
          }
          if (error instanceof ConnectException
              || error instanceof UnknownHostException
              || error instanceof SSLException
              || error instanceof PrematureCloseException) {
            return problems.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE");
          }
          return Mono.error(error);
        });
  }
}
