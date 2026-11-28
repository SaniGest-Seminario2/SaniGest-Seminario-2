package com.sanigest.sales.api.controller.invoice;

import com.sanigest.sales.api.dto.invoice.InvoiceDetailResponse;
import com.sanigest.sales.api.dto.sale.CancelSaleRequest;
import com.sanigest.sales.application.exception.sale.InvalidSaleRequest;
import com.sanigest.sales.application.usecase.invoice.ManageInvoices;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/invoices")
@PreAuthorize("isAuthenticated()")
public class InvoiceDocumentsController {
  private final ManageInvoices invoices;
  private final ObjectMapper json;

  InvoiceDocumentsController(ManageInvoices invoices, ObjectMapper json) {
    this.invoices = invoices;
    this.json = json;
  }

  public record Page(
      List<JsonNode> content, int page, int size, long totalElements, long totalPages) {}

  @GetMapping({"", "/collectible"})
  public Page page(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      jakarta.servlet.http.HttpServletRequest request) {
    var result = invoices.list(search, status, collectible(request), page, size);
    long total = result.totalElements();
    return new Page(
        result.content().stream().map(json::readTree).toList(),
        page,
        size,
        total,
        total / size + (total % size == 0 ? 0 : 1));
  }

  @GetMapping({"/all", "/collectible/all"})
  @ApiResponse(responseCode = "200", description = "Matching invoices in paginated order")
  @ApiResponse(
      responseCode = "422",
      description =
          "INVOICE_LIST_LIMIT_EXCEEDED when more than "
              + ManageInvoices.MAX_UNPAGED_RESULTS
              + " invoices match")
  public List<JsonNode> all(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status,
      jakarta.servlet.http.HttpServletRequest request) {
    return invoices.list(search, status, collectible(request), 0, null).content().stream()
        .map(json::readTree)
        .toList();
  }

  @GetMapping("/{id}")
  @ApiResponse(
      responseCode = "200",
      description = "Historical invoice snapshot with current payment status",
      content = @Content(schema = @Schema(implementation = InvoiceDetailResponse.class)))
  public JsonNode detail(@PathVariable String id) {
    return json.readTree(invoices.find(invoiceId(id)));
  }

  @PostMapping("/{id}/cancel")
  public JsonNode cancel(@PathVariable String id, @RequestBody CancelSaleRequest request) {
    return json.readTree(invoices.cancel(invoiceId(id), request.reason()));
  }

  private static boolean collectible(jakarta.servlet.http.HttpServletRequest request) {
    String path = request.getServletPath();
    return path.endsWith("/collectible") || path.endsWith("/collectible/all");
  }

  private static UUID invoiceId(String value) {
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
