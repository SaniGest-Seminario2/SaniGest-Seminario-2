package com.sanigest.company;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CompanyServiceApplication {

  @Bean
  Clock companyClock() {
    return Clock.systemUTC();
  }

  public static void main(String[] args) {
    SpringApplication.run(CompanyServiceApplication.class, args);
  }
}
