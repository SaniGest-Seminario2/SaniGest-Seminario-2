package com.sanigest.catalog.application.dto;

public record CatalogItemSummary(
    long total, long products, long services, long active, long inactive) {}
