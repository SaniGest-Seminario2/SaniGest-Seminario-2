package com.sanigest.sales.api.dto;

import static com.sanigest.sales.api.dto.ValueOption.of;

import com.sanigest.sales.domain.enums.PaymentMethod;
import com.sanigest.sales.domain.enums.ReceivableStatus;
import com.sanigest.sales.domain.enums.RequestedReceiptType;
import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.model.InternalInvoice;
import com.sanigest.sales.domain.model.InvoiceTaxRates;
import java.math.BigDecimal;
import java.util.List;

public record SalesOptions(
    List<ValueOption<String>> saleModes,
    List<ValueOption<String>> requestedReceiptTypes,
    List<ValueOption<String>> invoiceFilters,
    List<ValueOption<String>> receivableFilters,
    List<ValueOption<String>> paymentMethods,
    List<ValueOption<String>> saleModeFilters,
    List<ValueOption<BigDecimal>> invoiceTaxRates) {
  public static SalesOptions defaults() {
    var all = new ValueOption<>("ALL", "Todos");
    var cash = of(SaleMode.CASH, "Contado");
    var credit = of(SaleMode.CREDIT, "Crédito");
    return new SalesOptions(
        List.of(cash, credit),
        List.of(
            of(RequestedReceiptType.CONSUMPTION, "Consumidor final"),
            of(RequestedReceiptType.FISCAL_CREDIT, "Crédito fiscal")),
        List.of(
            all,
            of(InternalInvoice.Status.ISSUED, "Emitida"),
            of(ReceivableStatus.PARTIALLY_PAID, "Parcialmente pagada"),
            of(ReceivableStatus.PAID, "Pagada")),
        List.of(
            all,
            new ValueOption<>("PENDING", "Pendiente"),
            of(ReceivableStatus.PARTIALLY_PAID, "Parcial")),
        List.of(
            of(PaymentMethod.CASH, "Efectivo"),
            of(PaymentMethod.CREDIT_CARD, "Tarjeta de crédito"),
            of(PaymentMethod.DEBIT_CARD, "Tarjeta de débito"),
            of(PaymentMethod.TRANSFER, "Transferencia"),
            of(PaymentMethod.CHECK, "Cheque")),
        List.of(new ValueOption<>("ALL", "Todas"), cash, credit),
        List.of(
            new ValueOption<>(InvoiceTaxRates.STANDARD, "18%"),
            new ValueOption<>(InvoiceTaxRates.REDUCED, "16%"),
            new ValueOption<>(InvoiceTaxRates.EXEMPT, "0%/Exento")));
  }
}
