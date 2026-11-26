package com.sanigest.catalog.api.dto;

import static com.sanigest.catalog.api.dto.ValueOption.of;

import com.sanigest.catalog.domain.enums.CatalogItemType;
import com.sanigest.catalog.domain.enums.ClientPaymentTerms;
import com.sanigest.catalog.domain.enums.ClientType;
import com.sanigest.catalog.domain.enums.SupplierPaymentTerms;
import com.sanigest.catalog.domain.enums.SupplierSupplyType;
import java.util.List;

public record CatalogOptions(
    List<ValueOption<String>> clientTypes,
    List<ValueOption<String>> clientPaymentTerms,
    List<ValueOption<String>> supplierPaymentTerms,
    List<ValueOption<String>> supplierSupplyTypes,
    List<ValueOption<Integer>> supplierCreditDays,
    List<ValueOption<String>> itemTypes,
    List<ValueOption<String>> itemTypeFilters) {
  public static CatalogOptions defaults() {
    var types =
        List.of(of(CatalogItemType.PRODUCT, "Producto"), of(CatalogItemType.SERVICE, "Servicio"));
    return new CatalogOptions(
        List.of(of(ClientType.NATURAL, "Persona física"), of(ClientType.LEGAL, "Persona jurídica")),
        List.of(
            of(ClientPaymentTerms.CASH, "Contado"),
            of(ClientPaymentTerms.CREDIT, "Crédito"),
            of(ClientPaymentTerms.BOTH, "Ambos")),
        List.of(
            of(SupplierPaymentTerms.CASH, "Contado"),
            of(SupplierPaymentTerms.CREDIT, "Crédito"),
            of(SupplierPaymentTerms.BOTH, "Ambos")),
        List.of(
            of(SupplierSupplyType.PRODUCT, "Productos"),
            of(SupplierSupplyType.SERVICE, "Servicios"),
            of(SupplierSupplyType.BOTH, "Ambos")),
        List.of(
            new ValueOption<>(null, "No aplica"),
            new ValueOption<>(15, "15 días"),
            new ValueOption<>(30, "30 días"),
            new ValueOption<>(60, "60 días"),
            new ValueOption<>(90, "90 días")),
        types,
        List.of(new ValueOption<>("ALL", "Todos"), types.getFirst(), types.getLast()));
  }
}
