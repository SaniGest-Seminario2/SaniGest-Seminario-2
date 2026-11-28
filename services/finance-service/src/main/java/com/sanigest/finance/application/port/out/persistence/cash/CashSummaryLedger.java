package com.sanigest.finance.application.port.out.persistence.cash;

import com.sanigest.finance.application.dto.cash.CashSummary.Amounts;
import java.util.UUID;

public interface CashSummaryLedger {
  Amounts totals(UUID companyId, UUID shiftId);
}
