package com.sanigest.catalog.api.dto;

import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = CatalogItemStatusDeserializer.class)
public record CatalogItemStatusRequest(boolean active) {}
