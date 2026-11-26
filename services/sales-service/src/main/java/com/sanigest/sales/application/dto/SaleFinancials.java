package com.sanigest.sales.application.dto;

import com.sanigest.sales.domain.enums.ReceivableStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SaleFinancials(
    UUID invoiceId,
    String invoiceNumber,
    LocalDate dueDate,
    ReceivableStatus paymentStatus,
    BigDecimal outstandingBalance,
    BigDecimal collectedAmount) {}
