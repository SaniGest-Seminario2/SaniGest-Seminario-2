package com.sanigest.sales.application.dto.receivable;

import com.sanigest.sales.domain.model.receivable.Receivable;
import java.util.UUID;

public record ReceivableDetail(
    Receivable receivable, String saleCode, UUID invoiceId, String invoiceNumber) {}
