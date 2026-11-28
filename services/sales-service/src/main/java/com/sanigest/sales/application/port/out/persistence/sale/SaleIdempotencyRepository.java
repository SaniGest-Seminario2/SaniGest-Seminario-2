package com.sanigest.sales.application.port.out.persistence.sale;

import com.sanigest.sales.application.dto.sale.CreatedSaleResponse;
import java.util.UUID;
import java.util.function.Supplier;

public interface SaleIdempotencyRepository {
  record Request(
      UUID companyId, UUID actorId, String operation, String key, int version, String hash) {}

  sealed interface Resolution permits Claim, Replay {}

  record Claim(UUID token) implements Resolution {}

  record Replay(CreatedSaleResponse response) implements Resolution {}

  Resolution resolve(Request request);

  /** Runs local persistence under the claim and commits it together with its response. */
  CreatedSaleResponse complete(
      Request request, Claim claim, Supplier<CreatedSaleResponse> creation);

  void release(Request request, Claim claim);
}
