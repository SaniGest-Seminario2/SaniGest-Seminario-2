package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.model.SupplierDetails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSupplierRequest(
    @NotBlank @Size(max = SupplierDetails.NAME_LIMIT) String name,
    @Size(max = SupplierDetails.TAX_ID_LIMIT) String taxId,
    @NotBlank @Size(max = SupplierDetails.CONTACT_LIMIT) String contact,
    @Size(max = SupplierDetails.PHONE_LIMIT) @Pattern(regexp = SupplierDetails.PHONE_FORMAT) String phone,
    @Email @Pattern(regexp = SupplierDetails.EMAIL_FORMAT) @Size(max = SupplierDetails.EMAIL_LIMIT) String email) {

  public SupplierDetails toDetails() {
    return new SupplierDetails(name, taxId, contact, phone, email);
  }
}
