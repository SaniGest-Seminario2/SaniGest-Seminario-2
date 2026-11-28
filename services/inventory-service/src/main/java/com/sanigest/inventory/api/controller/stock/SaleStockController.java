package com.sanigest.inventory.api.controller.stock;

import com.sanigest.inventory.application.usecase.stock.SaleStockOperations;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Hidden
@RestController
@RequestMapping("/internal/v1/inventory/companies/{company}/sales/{sale}")
class SaleStockController {
  static final String TOKEN_HEADER = "X-Sale-Stock-Token";
  private final SaleStockOperations stock;
  private final byte[] token;

  SaleStockController(SaleStockOperations stock, @Value("${SALE_STOCK_TOKEN:}") String token) {
    this.stock = stock;
    this.token = token.isBlank() ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
  }

  @PostMapping("/issue")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void issue(
      @PathVariable UUID company,
      @PathVariable UUID sale,
      @RequestHeader(name = TOKEN_HEADER, defaultValue = "") String presented,
      @Valid @RequestBody Issue body) {
    authorize(presented);
    stock.issue(company, sale, body.actor(), body.quantities());
  }

  @PostMapping("/reverse")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void reverse(
      @PathVariable UUID company,
      @PathVariable UUID sale,
      @RequestHeader(name = TOKEN_HEADER, defaultValue = "") String presented,
      @RequestHeader("X-Sale-Stock-Actor") UUID actor) {
    authorize(presented);
    stock.reverse(company, sale, actor);
  }

  private void authorize(String presented) {
    if (token.length == 0
        || !MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
  }

  record Issue(
      @NotNull UUID actor,
      @NotEmpty @Size(max = 100) Map<UUID, @NotNull @Positive BigDecimal> quantities) {}
}
