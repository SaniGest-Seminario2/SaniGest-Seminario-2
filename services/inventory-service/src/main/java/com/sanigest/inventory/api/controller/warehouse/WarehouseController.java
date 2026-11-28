package com.sanigest.inventory.api.controller.warehouse;

import com.sanigest.inventory.api.dto.InventoryRequests;
import com.sanigest.inventory.api.dto.InventoryResponses.PageView;
import com.sanigest.inventory.api.dto.InventoryResponses.WarehouseView;
import com.sanigest.inventory.api.dto.NamedOption;
import com.sanigest.inventory.api.request.RequestContext;
import com.sanigest.inventory.application.dto.InventoryQueries;
import com.sanigest.inventory.application.dto.InventoryQueries.Order;
import com.sanigest.inventory.application.dto.InventoryQueries.Sort;
import com.sanigest.inventory.application.port.out.integration.company.InventoryAccess;
import com.sanigest.inventory.application.port.out.integration.company.InventoryAccess.Operation;
import com.sanigest.inventory.application.usecase.warehouse.WarehouseOperations;
import com.sanigest.inventory.domain.model.warehouse.Warehouse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/v1/warehouses")
public class WarehouseController {
  private final InventoryAccess access;
  private final WarehouseOperations warehouses;

  public WarehouseController(InventoryAccess access, WarehouseOperations warehouses) {
    this.access = access;
    this.warehouses = warehouses;
  }

  @PostMapping
  @ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true)
  ResponseEntity<WarehouseView> create(
      HttpServletRequest request, @Valid @RequestBody InventoryRequests.CreateWarehouse body) {
    var company = RequestContext.company(request);
    access.authorize(company, Operation.MANAGE_WAREHOUSE);
    var created = warehouses.create(company, body.command());
    return ResponseEntity.created(URI.create("/api/v1/warehouses/" + created.id()))
        .body(WarehouseView.from(created));
  }

  @GetMapping
  PageView<WarehouseView> list(
      HttpServletRequest request,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Warehouse.Status status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "CODE") Sort sort,
      @RequestParam(defaultValue = "ASC") Order order) {
    var company = RequestContext.company(request);
    access.authorize(company, Operation.READ);
    return PageView.from(
        warehouses.list(
            company,
            new InventoryQueries.Warehouses(
                search, status, new InventoryQueries.Paging(page, size, sort, order))),
        WarehouseView::from);
  }

  @GetMapping("/options")
  List<NamedOption> options(
      HttpServletRequest request, @RequestParam(required = false) String search) {
    var company = RequestContext.company(request);
    access.authorize(company, Operation.READ);
    var query =
        new InventoryQueries.Warehouses(
            search,
            Warehouse.Status.ACTIVE,
            new InventoryQueries.Paging(0, 20, Sort.NAME, Order.ASC));
    return warehouses.list(company, query).items().stream()
        .map(warehouse -> new NamedOption(warehouse.id(), warehouse.name()))
        .toList();
  }

  @GetMapping("/{id}")
  WarehouseView get(HttpServletRequest request, @PathVariable UUID id) {
    var company = RequestContext.company(request);
    access.authorize(company, Operation.READ);
    return WarehouseView.from(warehouses.get(company, id));
  }

  @PatchMapping("/{id}")
  WarehouseView patch(
      HttpServletRequest request, @PathVariable UUID id, @RequestBody ObjectNode body) {
    var company = RequestContext.company(request);
    access.authorize(company, Operation.MANAGE_WAREHOUSE);
    return WarehouseView.from(warehouses.patch(company, id, InventoryRequests.patch(body)));
  }
}
