package com.sanigest.expense.api.dto.expense;

import static com.sanigest.expense.api.dto.expense.ExpenseOptions.Option.of;

import com.sanigest.expense.application.dto.expense.ExpenseRegistration.Mode;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.OperationType;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.ReceiptStatus;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.ReceiptType;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.Tax;
import com.sanigest.expense.application.port.in.expense.ExpenseQueries.Status;
import com.sanigest.expense.application.port.in.payable.PayableQueries.Filter;
import java.util.List;

public record ExpenseOptions(
    List<Option> operationTypes,
    List<Option> receiptTypes,
    List<Option> modes,
    List<Option> receiptStatuses,
    List<Option> taxClassifications,
    List<Option> operationTypeFilters,
    List<Option> statusFilters,
    List<Option> payableFilters) {

  public static ExpenseOptions defaults() {
    var all = of(Filter.ALL, "Todos");
    var pending = of(Status.PENDING, "Pendiente");
    var partial = of(Status.PARTIALLY_PAID, "Parcial");
    return new ExpenseOptions(
        List.of(
            of(OperationType.PURCHASE, "Compra de mercancía o suministro"),
            of(OperationType.OPERATING_EXPENSE, "Gasto operativo"),
            of(OperationType.ASSET_ACQUISITION, "Adquisición de activo")),
        List.of(
            of(ReceiptType.FISCAL_CREDIT, "Crédito fiscal"),
            of(ReceiptType.CONSUMPTION, "Consumo"),
            of(ReceiptType.PURCHASE, "Compra"),
            of(ReceiptType.MINOR_EXPENSES, "Gastos menores"),
            of(ReceiptType.OTHER, "Otro"),
            of(ReceiptType.NOT_APPLICABLE, "No aplica")),
        List.of(of(Mode.CASH, "Contado"), of(Mode.CREDIT, "Crédito")),
        List.of(
            of(ReceiptStatus.PENDING, "Pendiente de recepción"),
            of(ReceiptStatus.COMPLETE, "Recibida completamente"),
            of(ReceiptStatus.PARTIAL, "Recibida parcialmente")),
        List.of(of(Tax.ITBIS_18, "18%"), of(Tax.ITBIS_16, "16%"), of(Tax.EXEMPT, "0%/Exento")),
        List.of(
            of(Filter.ALL, "Todas"),
            of(OperationType.PURCHASE, "Compra"),
            of(OperationType.OPERATING_EXPENSE, "Gasto"),
            of(OperationType.ASSET_ACQUISITION, "Activo")),
        List.of(all, of(Status.PAID, "Pagado"), pending, partial),
        List.of(all, pending, partial));
  }

  record Option(String value, String label) {
    static Option of(Enum<?> value, String label) {
      return new Option(value.name(), label);
    }
  }
}
