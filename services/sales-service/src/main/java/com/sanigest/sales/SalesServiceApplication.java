package com.sanigest.sales;

import com.sanigest.events.infrastructure.configuration.outbox.PaymentOutboxConfiguration;
import com.sanigest.web.api.documentation.OpenApiConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({PaymentOutboxConfiguration.class, OpenApiConfiguration.class})
public class SalesServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(SalesServiceApplication.class, args);
  }
}
