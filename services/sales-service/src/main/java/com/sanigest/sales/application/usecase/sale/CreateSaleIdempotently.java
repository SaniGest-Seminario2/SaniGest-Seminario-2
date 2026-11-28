package com.sanigest.sales.application.usecase.sale;

import static com.sanigest.sales.application.port.out.integration.company.SalesAuthorization.Permission.SALES_WRITE;

import com.sanigest.sales.application.dto.sale.CreateSaleCommand;
import com.sanigest.sales.application.dto.sale.CreatedSaleResponse;
import com.sanigest.sales.application.exception.sale.InvalidSaleRequest;
import com.sanigest.sales.application.port.out.integration.company.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.sale.SaleIdempotencyRepository;
import com.sanigest.sales.application.port.out.persistence.sale.SaleIdempotencyRepository.Claim;
import com.sanigest.sales.application.port.out.persistence.sale.SaleIdempotencyRepository.Replay;
import com.sanigest.sales.application.port.out.persistence.sale.SaleIdempotencyRepository.Request;
import com.sanigest.sales.domain.model.invoice.InvoiceTaxRates;
import com.sanigest.sales.domain.model.sale.Sale;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public final class CreateSaleIdempotently {
  private static final String INVOICE_OPERATION = "POST /api/v1/invoices";
  private final SalesAuthorization authorization;
  private final Supplier<UUID> actor;
  private final CreateSale createSale;
  private final SaleIdempotencyRepository idempotency;

  public CreateSaleIdempotently(
      SalesAuthorization authorization,
      Supplier<UUID> actor,
      CreateSale createSale,
      SaleIdempotencyRepository idempotency) {
    this.authorization = authorization;
    this.actor = actor;
    this.createSale = createSale;
    this.idempotency = idempotency;
  }

  public CreatedSaleResponse execute(
      String key, CreateSaleCommand command, Function<Sale, CreatedSaleResponse> response) {
    return executeForCompany(
        authorization.requireMembership(SALES_WRITE),
        SaleRequestFingerprint.OPERATION,
        key,
        command,
        response);
  }

  public CreatedSaleResponse createForInvoice(
      UUID company,
      String key,
      CreateSaleCommand command,
      Function<Sale, CreatedSaleResponse> response) {
    return executeForCompany(company, INVOICE_OPERATION, key, command, response);
  }

  private CreatedSaleResponse executeForCompany(
      UUID company,
      String operation,
      String key,
      CreateSaleCommand command,
      Function<Sale, CreatedSaleResponse> response) {
    UUID actorId = actor.get();
    CreateSale.validate(command);
    boolean invoice = INVOICE_OPERATION.equals(operation);
    if (invoice
        && command.lines().stream()
            .anyMatch(
                line -> line.unitPrice() == null || !InvoiceTaxRates.accepts(line.taxRate()))) {
      throw new InvalidSaleRequest();
    }
    if (!invoice
        && (command.hasInvoiceFields()
            || command.lines().stream().anyMatch(line -> line.discount() != null))) {
      throw new InvalidSaleRequest();
    }
    var request =
        new Request(
            company,
            actorId,
            operation,
            key,
            SaleRequestFingerprint.version(command),
            SaleRequestFingerprint.hash(command, invoice));
    return switch (idempotency.resolve(request)) {
      case Replay replay -> replay.response();
      case Claim claim -> create(request, claim, command, response, invoice);
    };
  }

  private CreatedSaleResponse create(
      Request request,
      Claim claim,
      CreateSaleCommand command,
      Function<Sale, CreatedSaleResponse> response,
      boolean invoice) {
    try {
      Sale prepared =
          invoice
              ? createSale.prepare(command, request.companyId(), true)
              : createSale.prepare(command, request.companyId());
      return idempotency.complete(
          request,
          claim,
          () -> {
            Sale registered =
                invoice
                    ? createSale.registerForInvoice(prepared, command.dueDate())
                    : createSale.register(prepared, command.dueDate());
            return response.apply(registered);
          });
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
