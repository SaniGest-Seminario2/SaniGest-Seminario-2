package com.sanigest.finance.application.port.in.movement;

import com.sanigest.finance.application.dto.movement.ManualMovement.Method;
import com.sanigest.finance.application.dto.movement.ManualMovement.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CashMovementQueries {
  int ALL_LIMIT = 1000;

  enum Origin {
    CASH_SESSION,
    RECEIVABLE_PAYMENT,
    PAYABLE_PAYMENT,
    MANUAL
  }

  record Criteria(
      String search,
      Type type,
      Origin origin,
      Method method,
      String category,
      LocalDate from,
      LocalDate to) {}

  record Row(
      UUID id,
      UUID referenceId,
      String reference,
      LocalDate date,
      String concept,
      String category,
      String method,
      BigDecimal amount,
      String currency,
      String type,
      String origin,
      UUID accountId,
      UUID destinationAccountId,
      String personOrEntity,
      String receipt,
      UUID documentId,
      String documentUrl,
      String observations,
      String status) {}

  record Page(List<Row> content, int page, int size, long totalElements, long totalPages) {}

  record Summary(
      BigDecimal inflows,
      BigDecimal outflows,
      BigDecimal netFlow,
      BigDecimal openingBalance,
      BigDecimal closingBalance,
      long movementCount) {}

  Page page(UUID company, Criteria criteria, int page, int size);

  List<Row> all(UUID company, Criteria criteria);

  Summary summary(UUID company, Criteria criteria);
}
