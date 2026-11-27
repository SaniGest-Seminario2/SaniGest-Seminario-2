package com.sanigest.expense;

import com.sanigest.events.outbox.PaymentOutboxConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(PaymentOutboxConfiguration.class)
public class ExpenseServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(ExpenseServiceApplication.class, args);
  }
}
