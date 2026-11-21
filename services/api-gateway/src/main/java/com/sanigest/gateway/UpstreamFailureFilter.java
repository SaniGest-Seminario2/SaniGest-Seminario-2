package com.sanigest.gateway;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.net.ssl.SSLException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

@Component
class UpstreamFailureFilter implements GlobalFilter, Ordered {
  @Override
  public int getOrder() {
    return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return chain
        .filter(exchange)
        .onErrorResume(error -> {
          if (error instanceof ResponseStatusException status
              && status.getStatusCode().equals(HttpStatus.GATEWAY_TIMEOUT)) {
            return problem(exchange, error, HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT");
          }
          if (error instanceof ConnectException
              || error instanceof UnknownHostException
              || error instanceof SSLException
              || error instanceof PrematureCloseException) {
            return problem(exchange, error, HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE");
          }
          return Mono.error(error);
        });
  }

  private Mono<Void> problem(
      ServerWebExchange exchange, Throwable error, HttpStatus status, String code) {
    var response = exchange.getResponse();
    if (!response.setStatusCode(status)) {
      return Mono.error(error);
    }
    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
    byte[] body =
        """
        {"type":"about:blank","title":"%s","status":%d,"errorCode":"%s","timestamp":"%s","traceId":"%s"}
        """
            .formatted(status.getReasonPhrase(), status.value(), code, Instant.now(), traceId)
            .getBytes(StandardCharsets.UTF_8);
    return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
  }
}
