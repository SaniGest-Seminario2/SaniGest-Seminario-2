package com.sanigest.sales.application.usecase.sale;

import com.sanigest.sales.application.exception.SaleNotFound;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission;
import com.sanigest.sales.application.port.out.persistence.SaleRepository;
import com.sanigest.sales.domain.model.Sale;
import java.util.UUID;

public final class GetSaleDetail {
  private final SalesAuthorization authorization;
  private final SaleRepository sales;

  public GetSaleDetail(SalesAuthorization authorization, SaleRepository sales) {
    this.authorization = authorization;
    this.sales = sales;
  }

  public Sale execute(UUID saleId) {
    UUID companyId = authorization.requireMembership(Permission.SALES_READ);
    return sales.findByCompanyIdAndId(companyId, saleId).orElseThrow(SaleNotFound::new);
  }
}
