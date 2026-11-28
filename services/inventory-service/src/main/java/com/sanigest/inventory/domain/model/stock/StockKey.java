package com.sanigest.inventory.domain.model.stock;

import java.util.UUID;

public record StockKey(UUID companyId, UUID catalogItemId, UUID warehouseId) {}
