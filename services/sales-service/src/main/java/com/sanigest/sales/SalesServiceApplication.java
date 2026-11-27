package com.sanigest.sales;

import com.sanigest.events.outbox.PaymentOutboxConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(PaymentOutboxConfiguration.class)
public class SalesServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(SalesServiceApplication.class, args);
  }
}
