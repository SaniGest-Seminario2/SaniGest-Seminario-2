package com.sanigest.catalog.api.dto.client;

import com.sanigest.catalog.application.dto.client.ClientSearchCriteria;
import com.sanigest.catalog.application.exception.client.InvalidClientQueryException;
import com.sanigest.catalog.domain.enums.client.ClientPaymentTerms;
import com.sanigest.catalog.domain.enums.client.ClientType;
import java.util.UUID;

public record ClientListFilter(
    String search,
    String status,
    ClientType clientType,
    UUID categoryId,
    ClientPaymentTerms paymentTerms,
    String sort) {
  public ClientSearchCriteria toCriteria() {
    boolean descending =
        switch (sort == null ? "name,asc" : sort) {
          case "name,asc" -> false;
          case "name,desc" -> true;
          default -> throw new InvalidClientQueryException();
        };
    return new ClientSearchCriteria(
        search, status, clientType, categoryId, paymentTerms, descending);
  }
}
