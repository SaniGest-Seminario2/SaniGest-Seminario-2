package com.sanigest.sales.infrastructure.integration.finance;

import com.sanigest.sales.application.exception.InvoicePaymentConflict;
import com.sanigest.sales.application.exception.PaymentAdmissionUnavailable;
import com.sanigest.sales.application.port.out.integration.ActiveCashShifts;
import com.sanigest.sales.application.port.out.integration.ActiveCashShifts.Shift;
import com.sanigest.sales.application.port.out.integration.PaymentAdmission;
import com.sanigest.sales.application.port.out.integration.PaymentAdmission.Admission;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sanigest.finance.enabled", havingValue = "true")
class FinanceAdmissionConfiguration {
  @Bean
  ActiveCashShifts financeActiveCashShifts(
      @Value("${sanigest.finance.origin}") URI origin,
      @Value("${sanigest.finance.admission-token}") String token,
      Environment environment) {
    validateConnection(origin, token, environment);

    var http =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(500))
            .followRedirects(HttpClient.Redirect.NEVER)
            .proxy(ProxySelector.of(null))
            .build();
    var transport = new JdkClientHttpRequestFactory(http);
    transport.setReadTimeout(Duration.ofSeconds(1));
    var client =
        RestClient.builder()
            .baseUrl(origin.toString())
            .requestFactory(transport)
            .defaultHeaders(headers -> headers.setBearerAuth(token))
            .build();

    return (company, operator) -> {
      try {
        var response =
            client
                .get()
                .uri(
                    "/internal/companies/{companyId}/operators/{operatorId}/active-shift",
                    company,
                    operator)
                .retrieve()
                .toEntity(Shift.class);
        var shift = response.getBody();
        if (response.getStatusCode().value() != 200
            || shift == null
            || shift.shiftId() == null
            || shift.accountsByMethod() == null) {
          throw new PaymentAdmissionUnavailable();
        }
        return Optional.of(shift);
      } catch (RestClientResponseException failure) {
        if (failure.getStatusCode().value() == 404) {
          return Optional.empty();
        }
        throw new PaymentAdmissionUnavailable();
      } catch (RestClientException failure) {
        throw new PaymentAdmissionUnavailable();
      }
    };
  }

  @Bean
  PaymentAdmission financePaymentAdmission(ActiveCashShifts shifts) {
    return (company, operator, payment, request) -> {
      var active =
          shifts
              .find(company, operator)
              .orElseThrow(() -> new InvoicePaymentConflict("Operator has no active cash shift"));
      Map<Integer, UUID> accountsByLine = new HashMap<>();
      for (int index = 0; index < request.lines().size(); index++) {
        String method = request.lines().get(index).method().name();
        UUID account = active.accountsByMethod().get(method);
        if (account == null) {
          throw new InvoicePaymentConflict("No account configured for payment method " + method);
        }
        accountsByLine.put(index, account);
      }
      return new Admission(active.shiftId(), Map.copyOf(accountsByLine));
    };
  }

  private static void validateConnection(URI origin, String token, Environment environment) {
    boolean secure = "https".equalsIgnoreCase(origin.getScheme());
    boolean local =
        environment.matchesProfiles("local", "test") && "http".equalsIgnoreCase(origin.getScheme());
    if ((!secure && !local)
        || origin.getHost() == null
        || origin.getUserInfo() != null
        || origin.getRawQuery() != null
        || origin.getRawFragment() != null
        || (!origin.getRawPath().isEmpty() && !origin.getRawPath().equals("/"))
        || token.isBlank()) {
      throw new IllegalArgumentException("Finance requires a dedicated origin and service token");
    }
  }
}
