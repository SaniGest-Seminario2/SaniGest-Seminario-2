package com.sanigest.sales.api.controller;

import com.sanigest.sales.api.dto.CreateSaleRequest;
import com.sanigest.sales.api.dto.SaleResponse;
import com.sanigest.sales.application.dto.CreatedSaleResponse;
import com.sanigest.sales.application.usecase.sale.CreateSaleIdempotently;
import jakarta.validation.Valid;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
public class SaleController {
  private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9._~-]{1,128}");
  private final CreateSaleIdempotently createSale;
  private final ObjectMapper json;

  SaleController(CreateSaleIdempotently createSale, ObjectMapper json) {
    this.createSale = createSale;
    this.json = json;
  }

  @PostMapping(path = "/api/v1/sales", produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<String> create(
      @RequestHeader HttpHeaders headers, @Valid @RequestBody CreateSaleRequest request) {
    var keys = headers.get("Idempotency-Key");
    if (keys == null || keys.size() != 1 || !KEY_FORMAT.matcher(keys.getFirst()).matches()) {
      throw new InvalidIdempotencyKey();
    }
    var result =
        createSale.execute(
            keys.getFirst(),
            request.toCommand(),
            sale ->
                new CreatedSaleResponse(
                    sale.id(),
                    HttpStatus.CREATED.value(),
                    json.writeValueAsString(SaleResponse.from(sale))));
    return ResponseEntity.status(result.status())
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }
}
