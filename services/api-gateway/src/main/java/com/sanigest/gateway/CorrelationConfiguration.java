package com.sanigest.gateway;

import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
class CorrelationConfiguration {
  private static final String REQUEST_ID = "X-Correlation-Id";

  @Bean
  WebFilter correlationId() {
    return (exchange, chain) -> {
      String id = UUID.randomUUID().toString();
      var request =
          exchange.getRequest().mutate().headers(headers -> headers.set(REQUEST_ID, id)).build();
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
