package com.sanigest.sales.domain.model;

import com.sanigest.sales.domain.enums.ReceivableOrigin;
import com.sanigest.sales.domain.enums.ReceivableStatus;
import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.enums.SaleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record Receivable(
    UUID id,
    UUID companyId,
    UUID saleId,
    UUID clientId,
    String clientName,
    BigDecimal totalAmount,
    BigDecimal balance,
    LocalDate dueDate,
    ReceivableStatus status,
    Instant createdAt,
    ReceivableOrigin origin) {

  public Receivable {
    Objects.requireNonNull(id);
    Objects.requireNonNull(companyId);
    Objects.requireNonNull(clientId);
    Objects.requireNonNull(createdAt);
    Objects.requireNonNull(origin);
    if ((origin == ReceivableOrigin.SALE) != (saleId != null)) {
      throw new IllegalArgumentException("Sale reference must match the origin");
    }
    clientName = SnapshotText.name(clientName, 250, "clientName");
    Sale.validateDate(dueDate);
    totalAmount = SaleAmounts.amount(totalAmount, "totalAmount");
    balance = SaleAmounts.amount(balance, "balance");
    int comparedToTotal = balance.compareTo(totalAmount);
    boolean validBalance =
        switch (status) {
          case OPEN, CANCELLED -> comparedToTotal == 0;
          case PARTIALLY_PAID -> balance.signum() > 0 && comparedToTotal < 0;
          case PAID -> balance.signum() == 0;
        };
    if (totalAmount.signum() <= 0 || !validBalance) {
      throw new IllegalArgumentException("Invalid receivable amount or balance for its status");
    }
  }

  public static Receivable fromSale(Sale sale, LocalDate dueDate, Instant createdAt) {
    if (sale.status() != SaleStatus.REGISTERED
        || dueDate == null
        || dueDate.isBefore(sale.saleDate())
        || (sale.mode() == SaleMode.CASH && !dueDate.equals(sale.saleDate()))) {
      throw new IllegalArgumentException(
          "Receivable requires a registered sale and a valid due date");
    }
    return new Receivable(
        sale.id(),
        sale.companyId(),
        sale.id(),
        sale.clientId(),
        sale.clientName(),
        sale.total(),
        sale.total(),
        dueDate,
        ReceivableStatus.OPEN,
        createdAt,
        ReceivableOrigin.SALE);
  }

  public Receivable cancel() {
    if (status == ReceivableStatus.CANCELLED) {
      return this;
    }
    if (status != ReceivableStatus.OPEN) {
      throw new IllegalStateException("Only an open receivable can be cancelled");
    }
    return new Receivable(
        id,
        companyId,
        saleId,
        clientId,
        clientName,
        totalAmount,
        balance,
        dueDate,
        ReceivableStatus.CANCELLED,
        createdAt,
        origin);
  }

  public BigDecimal collectibleBalance() {
    return status == ReceivableStatus.CANCELLED ? BigDecimal.ZERO.setScale(2) : balance;
  }

  public boolean isOverdue(LocalDate today) {
    return collectibleBalance().signum() > 0 && dueDate.isBefore(today);
  }

  public String currency() {
    return "DOP";
  }
}
