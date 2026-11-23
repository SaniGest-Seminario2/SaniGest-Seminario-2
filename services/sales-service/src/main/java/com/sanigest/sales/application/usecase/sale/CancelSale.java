package com.sanigest.sales.application.usecase.sale;

import com.sanigest.sales.application.exception.InvalidSaleRequest;
import com.sanigest.sales.application.exception.SaleNotFound;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission;
import com.sanigest.sales.application.port.out.persistence.SaleRepository;
import com.sanigest.sales.domain.model.Sale;
import com.sanigest.sales.domain.model.SaleCancellation;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

public final class CancelSale {
  private final SalesAuthorization authorization;
  private final Supplier<UUID> actor;
  private final SaleRepository sales;
  private final Clock clock;

  public CancelSale(
      SalesAuthorization authorization, Supplier<UUID> actor, SaleRepository sales, Clock clock) {
    this.authorization = authorization;
    this.actor = actor;
    this.sales = sales;
    this.clock = clock;
  }

  public Sale execute(UUID saleId, String reason) {
    UUID companyId = authorization.requireMembership(Permission.SALES_CANCEL);
    UUID actorId = actor.get();
    SaleCancellation cancellation;
    try {
      cancellation = new SaleCancellation(actorId, clock.instant(), reason);
    } catch (IllegalArgumentException invalid) {
      throw new InvalidSaleRequest();
    }
    return sales.cancel(companyId, saleId, cancellation).orElseThrow(SaleNotFound::new);
  }
}
