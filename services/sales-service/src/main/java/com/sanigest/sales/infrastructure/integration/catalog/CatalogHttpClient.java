package com.sanigest.sales.infrastructure.integration.catalog;

import static com.sanigest.sales.application.exception.CatalogResourceInvalidException.Reason.INACTIVE;
import static com.sanigest.sales.application.exception.CatalogResourceInvalidException.Reason.INVALID_REQUEST;
import static com.sanigest.sales.application.exception.CatalogResourceInvalidException.Reason.INVALID_RESPONSE;

import com.sanigest.sales.application.exception.CatalogResourceInvalidException;
import com.sanigest.sales.application.exception.CatalogUnavailableException;
import com.sanigest.sales.application.port.out.integration.CatalogClient;
import com.sanigest.sales.domain.model.CatalogCustomerSnapshot;
import com.sanigest.sales.domain.model.CatalogCustomerSnapshot.Status;
import com.sanigest.sales.domain.model.CatalogItemSnapshot;
import com.sanigest.sales.domain.model.CatalogItemSnapshot.Type;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

final class CatalogHttpClient implements CatalogClient {
  private static final BigDecimal PRICE_LIMIT = BigDecimal.TEN.pow(12);

  private final Optional<CatalogHttpApi> api;

  CatalogHttpClient(Optional<CatalogHttpApi> api) {
    this.api = api;
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
    var normalizedPrice = compatiblePrice(response.price());
    if (!response.active() || Boolean.FALSE.equals(response.availableForSale())) {
      throw new CatalogResourceInvalidException(INACTIVE);
    }
    return new CatalogItemSnapshot(
        catalogItemId,
        response.name(),
        type,
        response.description(),
        normalizedPrice,
        response.currency(),
        response.active());
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
    try {
      var response =
          request.apply(
              api.orElseThrow(() ->
                  new CatalogUnavailableException(
                      CatalogUnavailableException.Reason.NOT_CONFIGURED)));
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
