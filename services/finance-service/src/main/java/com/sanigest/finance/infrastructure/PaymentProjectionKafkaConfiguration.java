package com.sanigest.finance.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
class PaymentProjectionKafkaConfiguration {
  @Bean
  DefaultErrorHandler paymentProjectionErrorHandler() {
    return new DefaultErrorHandler(new FixedBackOff(5_000, FixedBackOff.UNLIMITED_ATTEMPTS));
  }
}
