package com.sanigest.sales.infrastructure.integration.catalog.dto;

public record CatalogCustomerResponse(
    String id,
    String name,
    String taxId,
    String email,
    String phone,
    String address,
    String status) {}
