package com.sanigest.inventory.domain.model;

import java.util.UUID;

public record StockKey(UUID companyId, UUID catalogItemId, UUID warehouseId) {}
