package com.sanigest.sales.application.dto.sale;

import com.sanigest.sales.domain.model.sale.Sale;

public record SaleDetail(Sale sale, SaleFinancials financials) {}
