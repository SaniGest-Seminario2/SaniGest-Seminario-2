package com.sanigest.gateway;

import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
class CorrelationConfiguration {
  private static final String REQUEST_ID = "X-Correlation-Id";

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  WebFilter correlationId() {
    return (exchange, chain) -> {
      String id = UUID.randomUUID().toString();
      String traceId = id.replace("-", "");
      String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
      var request =
          exchange
              .getRequest()
              .mutate()
              .headers(headers -> {
                headers.set(REQUEST_ID, id);
                headers.set("X-Trace-Id", traceId);
                headers.set("traceparent", "00-" + traceId + "-" + spanId + "-00");
                headers.remove("tracestate");
                headers.remove("baggage");
              })
              .build();
      exchange
          .getResponse()
          .beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(REQUEST_ID, id);
            return Mono.empty();
          });
      return chain.filter(exchange.mutate().request(request).build());
    };
  }
}
