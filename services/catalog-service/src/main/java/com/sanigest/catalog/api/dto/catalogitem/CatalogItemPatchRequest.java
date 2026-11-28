package com.sanigest.catalog.api.dto.catalogitem;

import com.sanigest.catalog.domain.model.catalogitem.CatalogItemPatch;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = CatalogItemPatchDeserializer.class)
public record CatalogItemPatchRequest(CatalogItemPatch patch) {}
