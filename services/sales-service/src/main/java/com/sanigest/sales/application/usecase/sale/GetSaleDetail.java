package com.sanigest.sales.application.usecase.sale;

import com.sanigest.sales.application.dto.SaleDetail;
import com.sanigest.sales.application.exception.SaleNotFound;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission;
import com.sanigest.sales.application.port.out.persistence.SaleQueryRepository;
import java.util.UUID;

public final class GetSaleDetail {
  private final SalesAuthorization authorization;
  private final SaleQueryRepository sales;

  public GetSaleDetail(SalesAuthorization authorization, SaleQueryRepository sales) {
    this.authorization = authorization;
    this.sales = sales;
  }

  public SaleDetail execute(UUID saleId) {
    UUID companyId = authorization.requireMembership(Permission.SALES_READ);
    return sales.detail(companyId, saleId).orElseThrow(SaleNotFound::new);
  }
}
