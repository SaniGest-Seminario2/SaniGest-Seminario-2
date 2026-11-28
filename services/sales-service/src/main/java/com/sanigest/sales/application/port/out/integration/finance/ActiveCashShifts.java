package com.sanigest.sales.application.port.out.integration.finance;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ActiveCashShifts {
  Optional<Shift> find(UUID company, UUID operator);

  record Shift(UUID shiftId, Map<String, UUID> accountsByMethod) {}
}
