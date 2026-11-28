package com.sanigest.company;

import com.sanigest.web.api.documentation.OpenApiConfiguration;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(OpenApiConfiguration.class)
public class CompanyServiceApplication {

  @Bean
  Clock companyClock() {
    return Clock.systemUTC();
  }

  public static void main(String[] args) {
    SpringApplication.run(CompanyServiceApplication.class, args);
  }
}
