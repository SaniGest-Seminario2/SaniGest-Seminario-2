package com.sanigest.catalog.api.dto.catalogitem;

import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = CatalogItemStatusDeserializer.class)
public record CatalogItemStatusRequest(boolean active) {}
