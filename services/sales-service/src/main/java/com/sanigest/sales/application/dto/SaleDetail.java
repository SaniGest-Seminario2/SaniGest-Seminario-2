package com.sanigest.sales.application.dto;

import com.sanigest.sales.domain.model.Sale;

public record SaleDetail(Sale sale, SaleFinancials financials) {}
