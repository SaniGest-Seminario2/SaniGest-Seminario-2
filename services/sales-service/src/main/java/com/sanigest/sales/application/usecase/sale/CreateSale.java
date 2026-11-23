package com.sanigest.sales.application.usecase.sale;

import static com.sanigest.sales.application.exception.CatalogResourceInvalidException.Reason.INACTIVE;
import static com.sanigest.sales.application.exception.CatalogResourceInvalidException.Reason.INVALID_RESPONSE;
import static com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission.SALES_WRITE;

import com.sanigest.sales.application.dto.CreateSaleCommand;
import com.sanigest.sales.application.exception.CatalogResourceInvalidException;
import com.sanigest.sales.application.exception.InvalidSaleRequest;
import com.sanigest.sales.application.exception.SaleAmountNotRepresentable;
import com.sanigest.sales.application.port.out.integration.CatalogClient;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.SaleRepository;
import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.model.CatalogCustomerSnapshot;
import com.sanigest.sales.domain.model.CatalogItemSnapshot;
import com.sanigest.sales.domain.model.Receivable;
import com.sanigest.sales.domain.model.Sale;
import com.sanigest.sales.domain.model.SaleLine;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.UUID;

public class CreateSale {
  private final SalesAuthorization authorization;
  private final CatalogClient catalog;
  private final SaleRepository sales;
  private final Clock clock;

  public CreateSale(
      SalesAuthorization authorization, CatalogClient catalog, SaleRepository sales, Clock clock) {
    this.authorization = authorization;
    this.catalog = catalog;
    this.sales = sales;
    this.clock = clock;
  }

  public Sale execute(CreateSaleCommand command) {
    UUID companyId = authorization.requireMembership(SALES_WRITE);
    validate(command);
    return register(prepare(command, companyId), command.dueDate());
  }

  Sale register(Sale sale, LocalDate dueDate) {
    if (sale.mode() == SaleMode.CREDIT) {
      return sales.register(sale, Receivable.fromSale(sale, dueDate, clock.instant()));
    }
    return sales.register(sale);
  }

  Sale prepare(CreateSaleCommand command, UUID companyId) {
    LocalDate date = effectiveDate(command);
    validateDueDate(command, date);
    var client = customer(command.clientId(), companyId);

    UUID saleId = UUID.randomUUID();
    // A repeated item within the command uses the same verified snapshot.
    var snapshots = new HashMap<UUID, CatalogItemSnapshot>();
    var lines =
        command.lines().stream()
            .map(line ->
                toSaleLine(
                    saleId,
                    line,
                    snapshots.computeIfAbsent(line.catalogItemId(), id -> item(id, companyId))))
            .toList();
    Sale sale;
    try {
      sale = Sale.register(saleId, companyId, client, date, command.mode(), lines);
    } catch (IllegalArgumentException invalid) {
      throw new SaleAmountNotRepresentable(invalid);
    }
    if (sale.mode() == SaleMode.CREDIT && sale.total().signum() <= 0) {
      throw new SaleAmountNotRepresentable(
          new IllegalArgumentException("Credit sale total must be positive"));
    }
    return sale;
  }

  private CatalogCustomerSnapshot customer(UUID clientId, UUID companyId) {
    var client = catalog.getCustomer(clientId, companyId);
    if (client == null || !clientId.equals(client.id()) || client.status() == null) {
      throw new CatalogResourceInvalidException(INVALID_RESPONSE);
    }
    if (client.status() != CatalogCustomerSnapshot.Status.ACTIVE) {
      throw new CatalogResourceInvalidException(INACTIVE);
    }

    return client;
  }

  private CatalogItemSnapshot item(UUID itemId, UUID companyId) {
    var item = catalog.getItem(itemId, companyId);
    if (item == null
        || !itemId.equals(item.id())
        || item.type() == null
        || !"DOP".equals(item.currency())) {
      throw new CatalogResourceInvalidException(INVALID_RESPONSE);
    }
    if (!item.active()) {
      throw new CatalogResourceInvalidException(INACTIVE);
    }
    return item;
  }

  private static SaleLine toSaleLine(
      UUID saleId, CreateSaleCommand.Line line, CatalogItemSnapshot item) {
    try {
      return SaleLine.fromCatalog(UUID.randomUUID(), saleId, item, line.quantity());
    } catch (IllegalArgumentException invalid) {
      throw new SaleAmountNotRepresentable(invalid);
    }
  }

  static void validate(CreateSaleCommand command) {
    if (command == null
        || command.clientId() == null
        || command.mode() == null
        || command.lines() == null
        || command.lines().isEmpty()
        || command.lines().size() > CreateSaleCommand.MAX_LINES) {
      throw new InvalidSaleRequest();
    }
    try {
      for (var line : command.lines()) {
        if (line == null || line.catalogItemId() == null) {
          throw new InvalidSaleRequest();
        }
        SaleLine.validateQuantity(line.quantity());
      }
      if (command.saleDate() != null) {
        Sale.validateDate(command.saleDate());
      }
    } catch (IllegalArgumentException invalid) {
      throw new InvalidSaleRequest();
    }
  }

  private static void validateDueDate(CreateSaleCommand command, LocalDate saleDate) {
    var dueDate = command.dueDate();
    if (command.mode() == SaleMode.CASH) {
      if (dueDate != null) {
        throw new InvalidSaleRequest();
      }
      return;
    }
    if (dueDate == null || dueDate.isBefore(saleDate) || dueDate.getYear() > 9999) {
      throw new InvalidSaleRequest();
    }
  }

  private LocalDate effectiveDate(CreateSaleCommand command) {
    try {
      return Sale.validateDate(
          command.saleDate() == null ? LocalDate.now(clock) : command.saleDate());
    } catch (IllegalArgumentException invalid) {
      throw new InvalidSaleRequest();
    }
  }
}
