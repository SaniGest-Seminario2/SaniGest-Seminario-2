package com.sanigest.sales.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

public record CancelSaleRequest(String reason) {
  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public static CancelSaleRequest from(@JsonProperty("reason") JsonNode reason) {
    if (reason == null || !reason.isString()) {
      throw new IllegalArgumentException("reason must be a JSON string");
    }
    return new CancelSaleRequest(reason.asString());
  }
}
