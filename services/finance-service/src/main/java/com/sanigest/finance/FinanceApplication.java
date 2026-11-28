package com.sanigest.finance;

import com.sanigest.web.api.documentation.OpenApiConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(OpenApiConfiguration.class)
public class FinanceApplication {
  public static void main(String[] args) {
    SpringApplication.run(FinanceApplication.class, args);
  }
}
