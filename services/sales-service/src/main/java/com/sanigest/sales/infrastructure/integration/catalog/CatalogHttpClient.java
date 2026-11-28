package com.sanigest.sales.infrastructure.integration.catalog;

import static com.sanigest.sales.application.exception.catalog.CatalogResourceInvalidException.Reason.INACTIVE;
import static com.sanigest.sales.application.exception.catalog.CatalogResourceInvalidException.Reason.INVALID_REQUEST;
import static com.sanigest.sales.application.exception.catalog.CatalogResourceInvalidException.Reason.INVALID_RESPONSE;

import com.sanigest.sales.application.exception.catalog.CatalogResourceInvalidException;
import com.sanigest.sales.application.exception.catalog.CatalogUnavailableException;
import com.sanigest.sales.application.port.out.integration.catalog.CatalogClient;
import com.sanigest.sales.domain.model.catalog.CatalogCustomerSnapshot;
import com.sanigest.sales.domain.model.catalog.CatalogCustomerSnapshot.Status;
import com.sanigest.sales.domain.model.catalog.CatalogItemSnapshot;
import com.sanigest.sales.domain.model.catalog.CatalogItemSnapshot.Type;
import com.sanigest.web.infrastructure.integration.resilience.RemoteCall;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.security.concurrent.DelegatingSecurityContextCallable;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

final class CatalogHttpClient implements CatalogClient {
  private static final BigDecimal PRICE_LIMIT = BigDecimal.TEN.pow(12);

  private final Optional<CatalogHttpApi> api;

  private final RemoteCall calls;

  CatalogHttpClient(Optional<CatalogHttpApi> api, RemoteCall calls) {
    this.api = api;
    this.calls = calls;
  }

  @Override
  public CatalogCustomerSnapshot getCustomer(UUID customerId, UUID expectedCompanyId) {
    var response =
        fetch(
            customerId,
            expectedCompanyId,
            proxy -> proxy.getCustomer(customerId, expectedCompanyId, expectedCompanyId));
    if (response == null
        || !matchesTenant(expectedCompanyId, response.companyId())
        || !matches(customerId, response.id())
        || !validText(response.name(), 160)) {
      throw new CatalogResourceInvalidException(INVALID_RESPONSE);
    }
    var status = requiredEnum(Status.class, response.status());
    if (status == Status.INACTIVE) {
      throw new CatalogResourceInvalidException(INACTIVE);
    }
    return new CatalogCustomerSnapshot(customerId, response.name(), status);
  }

  @Override
  public CatalogItemSnapshot getItem(UUID catalogItemId, UUID expectedCompanyId) {
    return getItem(catalogItemId, expectedCompanyId, false);
  }

  @Override
  public CatalogItemSnapshot getInvoiceItem(UUID catalogItemId, UUID expectedCompanyId) {
    return getItem(catalogItemId, expectedCompanyId, true);
  }

  private CatalogItemSnapshot getItem(UUID catalogItemId, UUID expectedCompanyId, boolean invoice) {
    var response =
        fetch(
            catalogItemId,
            expectedCompanyId,
            proxy -> proxy.getItem(catalogItemId, expectedCompanyId, expectedCompanyId));
    if (response == null
        || !matchesTenant(expectedCompanyId, response.companyId())
        || !matches(catalogItemId, response.id())
        || !validText(response.name(), 200)
        || !"DOP".equals(response.currency())
        || response.active() == null
        || (response.description() != null
            && (response.description().length() > 1000
                || response.description().indexOf(0) >= 0))) {
      throw new CatalogResourceInvalidException(INVALID_RESPONSE);
    }
    var type = requiredEnum(Type.class, response.type());
    var price = invoice ? invoicePrice(response.price()) : compatiblePrice(response.price());
    if (!response.active() || Boolean.FALSE.equals(response.availableForSale())) {
      throw new CatalogResourceInvalidException(INACTIVE);
    }
    return new CatalogItemSnapshot(
        catalogItemId,
        response.name(),
        type,
        response.description(),
        price,
        response.currency(),
        response.active(),
        invoice ? taxRate(response.taxRate()) : null);
  }

  private static BigDecimal taxRate(String value) {
    if (value == null) {
      return null;
    }
    try {
      var rate = new BigDecimal(value);
      if (rate.signum() >= 0
          && rate.compareTo(BigDecimal.ONE) < 0
          && rate.stripTrailingZeros().scale() <= 6) {
        return rate;
      }
    } catch (NumberFormatException invalid) {
      throw new CatalogResourceInvalidException(INVALID_RESPONSE);
    }
    throw new CatalogResourceInvalidException(INVALID_RESPONSE);
  }

  private static BigDecimal invoicePrice(BigDecimal price) {
    if (price == null
        || price.signum() < 0
        || price.compareTo(BigDecimal.TEN.pow(15)) >= 0
        || price.stripTrailingZeros().scale() > 4) {
      throw new CatalogResourceInvalidException(INVALID_RESPONSE);
    }
    return price.setScale(4);
  }

  private static BigDecimal compatiblePrice(BigDecimal price) {
    if (price == null || price.signum() < 0 || price.compareTo(PRICE_LIMIT) >= 0) {
      throw new CatalogResourceInvalidException(INVALID_RESPONSE);
    }
    var normalizedPrice = price.stripTrailingZeros();
    if (normalizedPrice.scale() > 2) {
      throw new CatalogResourceInvalidException(INVALID_RESPONSE);
    }
    return normalizedPrice.setScale(2);
  }

  private <T> T fetch(
      UUID id, UUID expectedCompanyId, Function<CatalogHttpApi, ResponseEntity<T>> request) {
    if (id == null || expectedCompanyId == null) {
      throw new CatalogResourceInvalidException(INVALID_REQUEST);
    }
    var client =
        api.orElseThrow(() ->
            new CatalogUnavailableException(CatalogUnavailableException.Reason.NOT_CONFIGURED));
    return calls.execute(
        new DelegatingSecurityContextCallable<>(
            () -> fetchVerified(client, expectedCompanyId, request)),
        () ->
            new CatalogUnavailableException(CatalogUnavailableException.Reason.TRANSPORT_FAILURE));
  }

  private static <T> T fetchVerified(
      CatalogHttpApi client,
      UUID expectedCompanyId,
      Function<CatalogHttpApi, ResponseEntity<T>> request) {
    try {
      var response = request.apply(client);
      if (response.getStatusCode().value() != 200) {
        throw new CatalogUnavailableException(
            CatalogUnavailableException.Reason.DEPENDENCY_FAILURE);
      }
      if (!java.util.List.of(expectedCompanyId.toString())
          .equals(response.getHeaders().getOrEmpty("X-Catalog-Company-Id"))) {
        throw new CatalogResourceInvalidException(INVALID_RESPONSE);
      }
      return response.getBody();
    } catch (ResourceAccessException failure) {
      throw new CatalogUnavailableException(CatalogUnavailableException.Reason.TRANSPORT_FAILURE);
    } catch (RestClientException failure) {
      for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
        if (cause instanceof IOException) {
          throw new CatalogUnavailableException(
              CatalogUnavailableException.Reason.TRANSPORT_FAILURE);
        }
      }
      throw new CatalogResourceInvalidException(INVALID_RESPONSE);
    }
  }

  private static boolean matchesTenant(UUID expectedCompany, String returnedCompany) {
    return returnedCompany == null || matches(expectedCompany, returnedCompany);
  }

  private static boolean matches(UUID requested, String received) {
    return requested.toString().equalsIgnoreCase(received);
  }

  private static boolean validText(String text, int maxLength) {
    return text != null && !text.isBlank() && text.length() <= maxLength && text.indexOf(0) < 0;
  }

  private static <T extends Enum<T>> T requiredEnum(Class<T> type, String value) {
    if (value != null) {
      try {
        return Enum.valueOf(type, value);
      } catch (IllegalArgumentException unknown) {
        throw new CatalogResourceInvalidException(INVALID_RESPONSE);
      }
    }
    throw new CatalogResourceInvalidException(INVALID_RESPONSE);
  }
}
