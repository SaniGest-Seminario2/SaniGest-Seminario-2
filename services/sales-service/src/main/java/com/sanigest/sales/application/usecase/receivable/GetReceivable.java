package com.sanigest.sales.application.usecase.receivable;

import com.sanigest.sales.application.dto.ReceivableDetail;
import com.sanigest.sales.application.exception.ReceivableNotFound;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission;
import com.sanigest.sales.application.port.out.persistence.ReceivableRepository;
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
