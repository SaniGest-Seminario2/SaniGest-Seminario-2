package com.sanigest.inventory.domain;

import java.util.UUID;

public record StockKey(UUID companyId, UUID catalogItemId, UUID warehouseId) {}
