package com.sanigest.catalog.api.dto.client;

import com.sanigest.catalog.domain.enums.client.ClientPaymentTerms;
import com.sanigest.catalog.domain.enums.client.ClientStatus;
import com.sanigest.catalog.domain.enums.client.ClientType;
import com.sanigest.catalog.domain.model.client.Client;
import java.util.UUID;

public record ClientDetailResponse(
    UUID id,
    String name,
    String taxId,
    String email,
    String phone,
    String address,
    ClientStatus status,
    ClientType clientType,
    String alternatePhone,
    String countryCode,
    String provinceCode,
    String municipalityCode,
    String preferredReceiptType,
    ClientPaymentTerms paymentTerms,
    UUID categoryId,
    String creditLimit,
    String observations) {
  public static ClientDetailResponse from(Client client) {
    var details = client.details();
    var profile = details.profile();
    return new ClientDetailResponse(
        client.id(),
        details.name(),
        details.taxId(),
        details.email(),
        details.phone(),
        details.address(),
        client.status(),
        profile == null ? null : profile.clientType(),
        profile == null ? null : profile.alternatePhone(),
        profile == null ? null : profile.countryCode(),
        profile == null ? null : profile.provinceCode(),
        profile == null ? null : profile.municipalityCode(),
        profile == null ? null : profile.preferredReceiptType(),
        profile == null ? null : profile.paymentTerms(),
        profile == null ? null : profile.categoryId(),
        profile == null || profile.creditLimit() == null
            ? null
            : profile.creditLimit().toPlainString(),
        profile == null ? null : profile.observations());
  }
}
