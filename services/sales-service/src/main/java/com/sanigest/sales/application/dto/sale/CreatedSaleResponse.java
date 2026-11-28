package com.sanigest.sales.application.dto.sale;

import java.util.UUID;

public record CreatedSaleResponse(UUID saleId, int status, String body) {}
