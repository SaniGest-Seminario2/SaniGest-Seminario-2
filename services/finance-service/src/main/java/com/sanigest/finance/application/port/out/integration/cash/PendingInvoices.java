package com.sanigest.finance.application.port.out.integration.cash;

import java.util.UUID;

public interface PendingInvoices {
  long count(UUID companyId, String accessToken);
}
