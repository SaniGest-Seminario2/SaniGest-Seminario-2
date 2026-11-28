package com.sanigest.sales.application.usecase.payment;

import com.sanigest.sales.application.port.out.integration.company.SalesAuthorization;
import com.sanigest.sales.application.port.out.integration.company.SalesAuthorization.Permission;
import com.sanigest.sales.application.port.out.integration.finance.PaymentAdmission;
import com.sanigest.sales.application.port.out.persistence.payment.InvoicePayments;
import com.sanigest.sales.domain.model.payment.InvoicePayment;
import java.util.UUID;
import java.util.function.Supplier;

public final class RegisterInvoicePayment {
  private final SalesAuthorization authorization;
  private final Supplier<UUID> actor;
  private final InvoicePayments payments;
  private final PaymentAdmission admission;

  public RegisterInvoicePayment(
      SalesAuthorization authorization,
      Supplier<UUID> actor,
      InvoicePayments payments,
      PaymentAdmission admission) {
    this.authorization = authorization;
    this.actor = actor;
    this.payments = payments;
    this.admission = admission;
  }

  public InvoicePayment.Result execute(UUID invoice, String key, InvoicePayment.Request request) {
    var company = authorization.requireMembership(Permission.SALES_WRITE);
    return payments.register(company, actor.get(), invoice, key, request, admission);
  }
}
