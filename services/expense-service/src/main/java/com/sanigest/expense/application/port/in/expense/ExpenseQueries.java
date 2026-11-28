package com.sanigest.expense.application.port.in.expense;

import com.sanigest.expense.application.dto.expense.ExpenseRegistration.Line;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.OperationType;
import com.sanigest.expense.application.exception.ExpenseFailure;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseQueries {
  Page page(UUID company, Criteria criteria, int page, int size);

  List<Row> all(UUID company, Criteria criteria);

  Optional<Detail> detail(UUID company, UUID id);

  Summary summary(UUID company, Criteria criteria);

  enum Status {
    PAID,
    PENDING,
    PARTIALLY_PAID,
    CANCELLED
  }

  record Criteria(
      String search, OperationType operationType, Status status, LocalDate from, LocalDate to) {
    public Criteria {
      search = search.strip();
      if (from != null && to != null && from.isAfter(to)) {
        throw ExpenseFailure.invalid("EXPENSE_INVALID_DATE_RANGE");
      }
    }
  }

  record Row(
      UUID id,
      String supplierInvoiceNumber,
      String concept,
      UUID supplierId,
      String supplierName,
      LocalDate documentDate,
      OperationType operationType,
      BigDecimal total,
      BigDecimal outstandingAmount,
      Status status) {}

  record Page(List<Row> content, int page, int size, long totalElements, long totalPages) {}

  record Detail(
      UUID id,
      String supplierInvoiceNumber,
      UUID supplierId,
      String supplierName,
      LocalDate documentDate,
      OperationType operationType,
      BigDecimal total,
      BigDecimal paidAmount,
      BigDecimal outstandingAmount,
      Status status,
      List<Line> lines,
      List<UUID> attachmentIds,
      Instant cancelledAt,
      String cancellationReason) {}

  record Summary(
      BigDecimal totalPurchases,
      BigDecimal operatingExpenses,
      BigDecimal assetAcquisitions,
      BigDecimal outstandingAmount,
      BigDecimal paidAmount,
      long operations) {}
}
