package com.sanigest.sales.api.controller;

import com.sanigest.sales.api.dto.InvoiceResponse;
import com.sanigest.sales.application.exception.InvalidSaleRequest;
import com.sanigest.sales.application.usecase.invoice.IssueInvoice;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
public class InvoiceController {
  private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9._~-]{1,128}");
  private final IssueInvoice issueInvoice;
  private final ObjectMapper json;

  InvoiceController(IssueInvoice issueInvoice, ObjectMapper json) {
    this.issueInvoice = issueInvoice;
    this.json = json;
  }

  @PostMapping(
      path = "/api/v1/sales/{saleId}/invoices",
      produces = MediaType.APPLICATION_JSON_VALUE)
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
  public ResponseEntity<String> issue(
      @PathVariable String saleId, @RequestHeader HttpHeaders headers, InputStream body)
      throws IOException {
    var keys = headers.get("Idempotency-Key");
    if (keys == null || keys.size() != 1 || !KEY_FORMAT.matcher(keys.getFirst()).matches()) {
      throw new InvalidIdempotencyKey();
    }
    UUID id;
    try {
      id = UUID.fromString(saleId);
    } catch (IllegalArgumentException invalid) {
      throw new InvalidSaleRequest();
    }
    if (!id.toString().equalsIgnoreCase(saleId) || body.read() != -1) {
      throw new InvalidSaleRequest();
    }
    String result =
        issueInvoice.execute(
            id, keys.getFirst(), invoice -> json.writeValueAsString(InvoiceResponse.from(invoice)));
    return ResponseEntity.status(HttpStatus.CREATED)
        .contentType(MediaType.APPLICATION_JSON)
        .body(result);
  }
}
