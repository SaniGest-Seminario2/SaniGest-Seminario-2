package com.sanigest.finance.application;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAdmission {
  Optional<ActiveShift> find(UUID companyId, UUID operatorId);

  boolean isEligibleAccount(UUID companyId, UUID accountId, String method);

  record ActiveShift(UUID shiftId, Map<String, UUID> accountsByMethod) {}
}
