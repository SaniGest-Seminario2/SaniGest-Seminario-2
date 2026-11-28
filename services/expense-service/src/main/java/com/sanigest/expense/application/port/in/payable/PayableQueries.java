package com.sanigest.expense.application.port.in.payable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PayableQueries {
  Page page(UUID company, Criteria criteria, int page, int size);

  List<Payable> all(UUID company, Criteria criteria);

  List<SupplierBalance> supplierBalances(UUID company, List<UUID> supplierIds);

  enum Filter {
    ALL,
    PENDING,
    PARTIALLY_PAID
  }

  enum Status {
    OPEN,
    PARTIALLY_PAID
  }

  record Criteria(String search, Filter status, UUID supplierId) {}

  record Payable(
      UUID id,
      UUID supplierId,
      String supplierName,
      String concept,
      LocalDate dueDate,
      BigDecimal originalAmount,
      BigDecimal paidAmount,
      BigDecimal outstandingAmount,
      Status status) {}

  record Page(List<Payable> content, int page, int size, long totalElements, long totalPages) {}

  record SupplierBalance(UUID supplierId, BigDecimal outstandingAmount) {}
}
