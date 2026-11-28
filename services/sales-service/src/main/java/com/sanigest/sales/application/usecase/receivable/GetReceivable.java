package com.sanigest.sales.application.usecase.receivable;

import com.sanigest.sales.application.dto.receivable.ReceivableDetail;
import com.sanigest.sales.application.exception.receivable.ReceivableNotFound;
import com.sanigest.sales.application.port.out.integration.company.SalesAuthorization;
import com.sanigest.sales.application.port.out.integration.company.SalesAuthorization.Permission;
import com.sanigest.sales.application.port.out.persistence.receivable.ReceivableRepository;
import java.util.UUID;

public final class GetReceivable {
  private final SalesAuthorization authorization;
  private final ReceivableRepository receivables;

  public GetReceivable(SalesAuthorization authorization, ReceivableRepository receivables) {
    this.authorization = authorization;
    this.receivables = receivables;
  }

  public ReceivableDetail execute(UUID id) {
    return receivables
        .findByCompanyIdAndId(authorization.requireMembership(Permission.SALES_READ), id)
        .orElseThrow(ReceivableNotFound::new);
  }
}
