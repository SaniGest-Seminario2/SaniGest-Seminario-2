package com.sanigest.sales.api.controller.sale;

import com.sanigest.sales.api.dto.sale.CancelSaleRequest;
import com.sanigest.sales.api.dto.sale.SaleDetailResponse;
import com.sanigest.sales.api.dto.sale.SaleResponse;
import com.sanigest.sales.application.exception.sale.InvalidSaleRequest;
import com.sanigest.sales.application.usecase.sale.CancelSale;
import com.sanigest.sales.application.usecase.sale.GetSaleDetail;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/sales/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("isAuthenticated()")
public class SaleLifecycleController {
  private final GetSaleDetail getSaleDetail;
  private final CancelSale cancelSale;

  SaleLifecycleController(GetSaleDetail getSaleDetail, CancelSale cancelSale) {
    this.getSaleDetail = getSaleDetail;
    this.cancelSale = cancelSale;
  }

  @GetMapping
  public SaleDetailResponse detail(@PathVariable String id) {
    return SaleDetailResponse.from(getSaleDetail.execute(saleId(id)));
  }

  @PostMapping(path = "/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(responseCode = "200", description = "Cancelled sale", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "404", description = "SALE_NOT_FOUND")
  @ApiResponse(
      responseCode = "409",
      description = "SALE_CANCELLATION_CONFLICT, including confirmed payments")
  public SaleResponse cancel(@PathVariable String id, @RequestBody CancelSaleRequest request) {
    return SaleResponse.from(cancelSale.execute(saleId(id), request.reason()));
  }

  private static UUID saleId(String value) {
    try {
      UUID id = UUID.fromString(value);
      if (id.toString().equalsIgnoreCase(value)) {
        return id;
      }
    } catch (IllegalArgumentException invalid) {
      throw new InvalidSaleRequest();
    }
    throw new InvalidSaleRequest();
  }
}
