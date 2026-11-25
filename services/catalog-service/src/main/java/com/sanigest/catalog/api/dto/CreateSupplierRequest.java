package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.model.SupplierDetails;
import tools.jackson.databind.JsonNode;

public record CreateSupplierRequest(SupplierDetails details) {
  public static CreateSupplierRequest parse(JsonNode body) {
    return new CreateSupplierRequest(SupplierPatchRequest.creation(body).createDetails());
  }
}
