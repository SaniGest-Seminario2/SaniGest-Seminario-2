package com.sanigest.finance.application.port.out.persistence;

import com.sanigest.finance.application.dto.CashSummary.Amounts;
import java.util.UUID;

public interface CashSummaryLedger {
  Amounts totals(UUID companyId, UUID shiftId);
}
