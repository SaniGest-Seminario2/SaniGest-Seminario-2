package com.sanigest.sales.api.controller;

import com.sanigest.sales.api.dto.CreateInvoiceRequest;
import com.sanigest.sales.api.dto.InvoiceResponse;
import com.sanigest.sales.application.usecase.invoice.IssueInvoice;
import com.sanigest.sales.application.usecase.sale.CreateSaleIdempotently;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
public class CreateInvoiceController {
  private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9._~-]{1,128}");
  private final CreateSaleIdempotently createSale;
  private final IssueInvoice issueInvoice;
  private final ObjectMapper json;

  CreateInvoiceController(
      CreateSaleIdempotently createSale, IssueInvoice issueInvoice, ObjectMapper json) {
    this.createSale = createSale;
    this.issueInvoice = issueInvoice;
    this.json = json;
  }

  @PostMapping(path = "/api/v1/invoices", produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("isAuthenticated()")
  @ApiResponse(
      responseCode = "201",
      description = "Created",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = InvoiceResponse.class)))
  @Parameter(
      name = "Idempotency-Key",
      in = ParameterIn.HEADER,
      required = true,
      schema = @Schema(type = "string", pattern = "^[A-Za-z0-9._~-]{1,128}$"))
  public ResponseEntity<String> create(
      @RequestHeader HttpHeaders headers, @Valid @RequestBody CreateInvoiceRequest request) {
    var keys = headers.get("Idempotency-Key");
    if (keys == null || keys.size() != 1 || !KEY_FORMAT.matcher(keys.getFirst()).matches()) {
      throw new InvalidIdempotencyKey();
    }
    var form = request.toCommand();
    var result =
        issueInvoice.create(
            createSale,
            keys.getFirst(),
            form,
            invoice -> json.writeValueAsString(InvoiceResponse.from(invoice, form)));
    return ResponseEntity.status(result.status())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }
}
