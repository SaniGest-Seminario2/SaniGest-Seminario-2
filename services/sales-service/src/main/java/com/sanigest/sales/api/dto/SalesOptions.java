package com.sanigest.sales.api.dto;

import static com.sanigest.sales.api.dto.ValueOption.of;

import com.sanigest.sales.domain.enums.PaymentMethod;
import com.sanigest.sales.domain.enums.ReceivableStatus;
import com.sanigest.sales.domain.enums.RequestedReceiptType;
import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.model.InternalInvoice;
import java.util.List;

public record SalesOptions(
    List<ValueOption<String>> saleModes,
    List<ValueOption<String>> requestedReceiptTypes,
    List<ValueOption<String>> invoiceFilters,
    List<ValueOption<String>> receivableFilters,
    List<ValueOption<String>> paymentMethods) {
  public static SalesOptions defaults() {
    var all = new ValueOption<>("ALL", "Todos");
    var partial = of(ReceivableStatus.PARTIALLY_PAID, "Parcialmente pagada");
    return new SalesOptions(
        List.of(of(SaleMode.CASH, "Contado"), of(SaleMode.CREDIT, "Crédito")),
        List.of(
            of(RequestedReceiptType.CONSUMPTION, "Consumo"),
            of(RequestedReceiptType.FISCAL_CREDIT, "Crédito fiscal")),
        List.of(
            all,
            of(InternalInvoice.Status.ISSUED, "Emitida"),
            partial,
            of(ReceivableStatus.PAID, "Pagada")),
        List.of(all, new ValueOption<>("PENDING", "Pendiente"), partial),
        List.of(
            of(PaymentMethod.CASH, "Efectivo"),
            of(PaymentMethod.CREDIT_CARD, "Tarjeta de crédito"),
            of(PaymentMethod.DEBIT_CARD, "Tarjeta de débito"),
            of(PaymentMethod.TRANSFER, "Transferencia"),
            of(PaymentMethod.CHECK, "Cheque")));
  }
}
