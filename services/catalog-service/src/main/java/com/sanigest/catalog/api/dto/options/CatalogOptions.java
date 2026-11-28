package com.sanigest.catalog.api.dto.options;

import static com.sanigest.catalog.api.dto.ValueOption.of;

import com.sanigest.catalog.api.dto.ValueOption;
import com.sanigest.catalog.application.port.out.integration.inventory.InventoryAvailability.State;
import com.sanigest.catalog.domain.enums.catalogitem.CatalogItemType;
import com.sanigest.catalog.domain.enums.client.ClientPaymentTerms;
import com.sanigest.catalog.domain.enums.client.ClientType;
import com.sanigest.catalog.domain.enums.supplier.SupplierPaymentTerms;
import com.sanigest.catalog.domain.enums.supplier.SupplierSupplyType;
import java.util.List;

public record CatalogOptions(
    List<ValueOption<String>> clientTypes,
    List<ValueOption<String>> clientPaymentTerms,
    List<ValueOption<String>> supplierPaymentTerms,
    List<ValueOption<String>> supplierSupplyTypes,
    List<ValueOption<Integer>> supplierCreditDays,
    List<ValueOption<String>> itemTypes,
    List<ValueOption<String>> itemTypeFilters,
    List<ValueOption<String>> itemAvailabilityFilters,
    List<ValueOption<String>> supplierSupplyClassifications) {
  public static CatalogOptions defaults() {
    var types =
        List.of(of(CatalogItemType.PRODUCT, "Producto"), of(CatalogItemType.SERVICE, "Servicio"));
    var products = of(SupplierSupplyType.PRODUCT, "Productos");
    var services = of(SupplierSupplyType.SERVICE, "Servicios");
    return new CatalogOptions(
        List.of(of(ClientType.NATURAL, "Persona física"), of(ClientType.LEGAL, "Persona jurídica")),
        List.of(of(ClientPaymentTerms.CASH, "Contado"), of(ClientPaymentTerms.CREDIT, "Crédito")),
        List.of(
            of(SupplierPaymentTerms.CASH, "Contado"),
            of(SupplierPaymentTerms.CREDIT, "Crédito"),
            of(SupplierPaymentTerms.BOTH, "Ambos")),
        List.of(products, services, of(SupplierSupplyType.BOTH, "Ambos")),
        List.of(
            new ValueOption<>(null, "No aplica"),
            new ValueOption<>(15, "15 días"),
            new ValueOption<>(30, "30 días"),
            new ValueOption<>(60, "60 días"),
            new ValueOption<>(90, "90 días")),
        types,
        List.of(new ValueOption<>("ALL", "Todos"), types.getFirst(), types.getLast()),
        List.of(
            new ValueOption<>("ALL", "Todas"),
            of(State.AVAILABLE, "Disponibles"),
            of(State.LOW, "Stock bajo"),
            of(State.OUT, "Agotado")),
        List.of(
            of(SupplierSupplyType.REGULAR, "Regular"),
            of(SupplierSupplyType.BOTH, "Productos y servicios"),
            products,
            services));
  }
}
