package com.sanigest.catalog.api.dto.supplier;

import com.sanigest.catalog.domain.enums.supplier.SupplierStatus;
import com.sanigest.catalog.domain.exception.supplier.InvalidSupplierPatch;
import com.sanigest.catalog.domain.model.supplier.SupplierDetails;
import com.sanigest.catalog.domain.model.supplier.SupplierItemOffer;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

public record CreateSupplierRequest(
    SupplierDetails details, SupplierStatus status, List<SupplierItemOffer> items) {
  private static final List<Integer> CREDIT_DAYS = List.of(15, 30, 60, 90);

  public static CreateSupplierRequest parse(JsonNode body) {
    if (!(body instanceof ObjectNode object) || !body.has("creditDays")) {
      throw new InvalidSupplierPatch();
    }
    var profile = object.deepCopy();
    var status = profile.remove("status");
    var items = profile.remove("items");
    if (!body.has("paymentTerms") && body.path("creditDays").isIntegralNumber()) {
      profile.put("paymentTerms", "BOTH");
    }
    var details = SupplierPatchRequest.details(profile).createDetails();
    if (details.taxId() == null
        || details.phone() == null
        || details.email() == null
        || details.profile().supplyType() == null
        || details.profile().categoryId() == null
        || (details.profile().creditDays() != null
            && !CREDIT_DAYS.contains(details.profile().creditDays()))) {
      throw new InvalidSupplierPatch();
    }
    return new CreateSupplierRequest(
        details,
        SupplierPatchRequest.status(object.objectNode().set("status", status)),
        SupplierItemsRequest.parseOffers(items, true));
  }
}
