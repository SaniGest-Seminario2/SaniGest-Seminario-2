package com.sanigest.catalog.api.dto.catalogitem;

import com.sanigest.catalog.domain.enums.catalogitem.CatalogItemType;
import com.sanigest.catalog.domain.model.catalogitem.CatalogItemProfile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = CreateCatalogItemDeserializer.class)
public record CreateCatalogItemRequest(
    @NotBlank String name,
    @NotNull CatalogItemType type,
    @NotNull BigDecimal price,
    String description,
    CatalogItemProfile profile) {}
