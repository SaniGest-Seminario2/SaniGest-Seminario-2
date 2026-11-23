package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.model.CatalogItemPatch;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = CatalogItemPatchDeserializer.class)
public record CatalogItemPatchRequest(CatalogItemPatch patch) {}
