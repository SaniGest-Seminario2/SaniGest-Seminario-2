package com.sanigest.expense.infrastructure.integration.finance;

import com.sanigest.events.domain.enums.payment.PaymentMethodV1;
import com.sanigest.expense.application.exception.ExpenseFailure;
import com.sanigest.expense.application.port.out.integration.finance.PaymentAccounts;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class FinancePaymentAccounts implements PaymentAccounts {
  private final RestClient finance;
  private final String token;

  public FinancePaymentAccounts(
      @Value("${sanigest.finance-origin}") URI origin,
      @Value("${sanigest.finance-admission-token}") String token) {
    var requests = new SimpleClientHttpRequestFactory();
    requests.setConnectTimeout(Duration.ofMillis(500));
    requests.setReadTimeout(Duration.ofSeconds(2));
    finance = RestClient.builder().baseUrl(origin.toString()).requestFactory(requests).build();
    this.token = token;
  }

  @Override
  public void requireEligible(UUID company, UUID account, PaymentMethodV1 method) {
    if (token.isBlank()) {
      throw unavailable();
    }
    try {
      finance
          .get()
          .uri(
              "/internal/companies/{companyId}/payment-accounts/{accountId}?method={method}",
              company,
              account,
              method.name())
          .headers(headers -> headers.setBearerAuth(token))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException failure) {
      if (failure.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
        throw ExpenseFailure.invalid("PAYABLE_INELIGIBLE_PAYMENT_ACCOUNT");
      }
      throw unavailable();
    } catch (RestClientException failure) {
      throw unavailable();
    }
  }

  private static ExpenseFailure unavailable() {
    return new ExpenseFailure(
        ExpenseFailure.Kind.UNAVAILABLE, "PAYABLE_PAYMENT_ACCOUNTS_UNAVAILABLE");
  }
}
