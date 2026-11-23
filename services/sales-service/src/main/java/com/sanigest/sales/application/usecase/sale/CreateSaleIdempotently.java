package com.sanigest.sales.application.usecase.sale;

import static com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission.SALES_WRITE;

import com.sanigest.sales.application.dto.CreateSaleCommand;
import com.sanigest.sales.application.dto.CreatedSaleResponse;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.SaleIdempotencyRepository;
import com.sanigest.sales.application.port.out.persistence.SaleIdempotencyRepository.Claim;
import com.sanigest.sales.application.port.out.persistence.SaleIdempotencyRepository.Replay;
import com.sanigest.sales.application.port.out.persistence.SaleIdempotencyRepository.Request;
import com.sanigest.sales.application.port.out.persistence.SaleRepository;
import com.sanigest.sales.domain.model.Sale;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public final class CreateSaleIdempotently {
  private final SalesAuthorization authorization;
  private final Supplier<UUID> actor;
  private final CreateSale createSale;
  private final SaleRepository sales;
  private final SaleIdempotencyRepository idempotency;

  public CreateSaleIdempotently(
      SalesAuthorization authorization,
      Supplier<UUID> actor,
      CreateSale createSale,
      SaleRepository sales,
      SaleIdempotencyRepository idempotency) {
    this.authorization = authorization;
    this.actor = actor;
    this.createSale = createSale;
    this.sales = sales;
    this.idempotency = idempotency;
  }

  public CreatedSaleResponse execute(
      String key, CreateSaleCommand command, Function<Sale, CreatedSaleResponse> response) {
    UUID company = authorization.requireMembership(SALES_WRITE);
    UUID actorId = actor.get();
    CreateSale.validate(command);
    var request =
        new Request(
            company,
            actorId,
            SaleRequestFingerprint.OPERATION,
            key,
            SaleRequestFingerprint.VERSION,
            SaleRequestFingerprint.hash(command));
    return switch (idempotency.resolve(request)) {
      case Replay replay -> replay.response();
      case Claim claim -> create(request, claim, command, response);
    };
  }

  private CreatedSaleResponse create(
      Request request,
      Claim claim,
      CreateSaleCommand command,
      Function<Sale, CreatedSaleResponse> response) {
    try {
      Sale prepared = createSale.prepare(command, request.companyId());
      return idempotency.complete(request, claim, () -> response.apply(sales.register(prepared)));
    } catch (RuntimeException failure) {
      try {
        idempotency.release(request, claim);
      } catch (RuntimeException releaseFailure) {
        failure.addSuppressed(releaseFailure);
      }
      throw failure;
    }
  }
}
