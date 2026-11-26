package com.sanigest.sales.application.dto;

import com.sanigest.sales.domain.model.Receivable;
import java.util.UUID;

public record ReceivableDetail(
    Receivable receivable, String saleCode, UUID invoiceId, String invoiceNumber) {}
