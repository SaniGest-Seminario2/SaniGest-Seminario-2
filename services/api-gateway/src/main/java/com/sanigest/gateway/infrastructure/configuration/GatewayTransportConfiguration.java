package com.sanigest.gateway.infrastructure.configuration;

import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class GatewayTransportConfiguration {
  @Bean
  HttpClientCustomizer disableTransportRetry() {
    return client -> client.disableRetry(true);
  }
}
