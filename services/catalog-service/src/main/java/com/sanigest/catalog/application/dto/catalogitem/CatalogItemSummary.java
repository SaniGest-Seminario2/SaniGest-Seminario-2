package com.sanigest.catalog.application.dto.catalogitem;

public record CatalogItemSummary(
    long total, long products, long services, long active, long inactive) {}
