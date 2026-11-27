package com.sanigest.inventory.api.controller;

import com.sanigest.inventory.api.dto.InventoryRequests.PurchaseReceipt;
import com.sanigest.inventory.application.port.out.integration.InventoryAccess;
import com.sanigest.inventory.application.port.out.integration.InventoryAccess.Operation;
import com.sanigest.inventory.application.usecase.StockOperations;
import com.sanigest.inventory.domain.StockKey;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
class PurchaseReceiptController {
  private final InventoryAccess access;
  private final StockOperations stock;

  PurchaseReceiptController(InventoryAccess access, StockOperations stock) {
    this.access = access;
    this.stock = stock;
  }

  @PostMapping("/internal/v1/inventory/purchase-receipts")
  @ResponseStatus(HttpStatus.CREATED)
  ReceiptMovement receive(HttpServletRequest request, @Valid @RequestBody PurchaseReceipt body) {
    var company = RequestContext.company(request);
    var actor = access.authorize(company, Operation.ADJUST_STOCK);
    var movement =
        stock.receivePurchase(
            new StockKey(company, body.catalogItemId(), body.warehouseId()),
            actor,
            RequestContext.idempotency(request),
            body.expenseId(),
            body.receivedQuantity());
    return new ReceiptMovement(movement.id());
  }

  record ReceiptMovement(UUID id) {}
}
