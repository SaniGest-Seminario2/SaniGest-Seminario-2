package com.sanigest.inventory.api.controller;

import com.sanigest.inventory.api.dto.InventoryOptions;
import com.sanigest.inventory.api.dto.InventoryRequests;
import com.sanigest.inventory.api.dto.InventoryResponses.MovementView;
import com.sanigest.inventory.api.dto.InventoryResponses.PageView;
import com.sanigest.inventory.api.dto.InventoryResponses.StockView;
import com.sanigest.inventory.api.dto.InventoryResponses.SummaryView;
import com.sanigest.inventory.api.dto.MovementFilters;
import com.sanigest.inventory.api.dto.StockFilters;
import com.sanigest.inventory.application.port.out.integration.InventoryAccess;
import com.sanigest.inventory.application.port.out.integration.InventoryAccess.Operation;
import com.sanigest.inventory.application.usecase.InventoryReads;
import com.sanigest.inventory.application.usecase.StockOperations;
import com.sanigest.inventory.domain.model.StockKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
  private final InventoryAccess access;
  private final StockOperations stock;
  private final InventoryReads reads;

  public InventoryController(InventoryAccess access, StockOperations stock, InventoryReads reads) {
    this.access = access;
    this.stock = stock;
    this.reads = reads;
  }

  @GetMapping("/options")
  InventoryOptions options(HttpServletRequest request) {
    access.authorize(RequestContext.company(request), Operation.READ);
    return InventoryOptions.defaults();
  }

  @GetMapping("/stock/{itemId}/{warehouseId}")
  StockView get(
      HttpServletRequest request, @PathVariable UUID itemId, @PathVariable UUID warehouseId) {
    var company = RequestContext.company(request);
    access.authorize(company, Operation.READ);
    var key = new StockKey(company, itemId, warehouseId);
    var detail = reads.get(key);
    return detail
        .position()
        .map(position -> StockView.from(position, detail.warehouseName()))
        .orElseGet(() -> StockView.absent(key, detail.warehouseName(), detail.item()));
  }

  @PutMapping("/stock/{itemId}/{warehouseId}")
  StockView configure(
      HttpServletRequest request,
      @PathVariable UUID itemId,
      @PathVariable UUID warehouseId,
      @Valid @RequestBody InventoryRequests.Configure body) {
    var company = RequestContext.company(request);
    var actor = access.authorize(company, Operation.CONFIGURE_STOCK);
    var key = new StockKey(company, itemId, warehouseId);
    var name = reads.warehouseName(key);
    var position = stock.configure(key, actor, RequestContext.idempotency(request), body.command());
    return StockView.from(position, name);
  }

  @PostMapping("/adjustments")
  @ResponseStatus(HttpStatus.CREATED)
  MovementView adjust(
      HttpServletRequest request, @Valid @RequestBody InventoryRequests.Adjust body) {
    var company = RequestContext.company(request);
    var actor = access.authorize(company, Operation.ADJUST_STOCK);
    return MovementView.from(
        stock.adjust(
            new StockKey(company, body.catalogItemId(), body.warehouseId()),
            actor,
            RequestContext.idempotency(request),
            body.command()));
  }

  @GetMapping("/stock")
  PageView<StockView> list(
      HttpServletRequest request, @ParameterObject @ModelAttribute StockFilters filters) {
    var company = RequestContext.company(request);
    access.authorize(company, Operation.READ);
    return PageView.from(
        reads.list(company, filters.query()),
        entry -> StockView.from(entry.position(), entry.warehouseName()));
  }

  @GetMapping("/movements")
  PageView<MovementView> movements(
      HttpServletRequest request, @ParameterObject @ModelAttribute MovementFilters filters) {
    var company = RequestContext.company(request);
    access.authorize(company, Operation.READ);
    return PageView.from(reads.movements(company, filters.query()), MovementView::from);
  }

  @GetMapping("/summary")
  SummaryView summary(
      HttpServletRequest request, @RequestParam(required = false) UUID warehouseId) {
    var company = RequestContext.company(request);
    access.authorize(company, Operation.READ);
    return SummaryView.from(reads.summary(company, warehouseId));
  }
}
