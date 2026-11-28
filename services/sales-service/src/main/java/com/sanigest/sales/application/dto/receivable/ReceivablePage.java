package com.sanigest.sales.application.dto.receivable;

import java.util.List;

public record ReceivablePage(
    List<ReceivableDetail> content, int page, int size, long totalElements) {}
