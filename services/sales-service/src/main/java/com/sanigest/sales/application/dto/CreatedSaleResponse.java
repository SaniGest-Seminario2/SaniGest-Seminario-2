package com.sanigest.sales.application.dto;

import java.util.UUID;

public record CreatedSaleResponse(UUID saleId, int status, String body) {}
