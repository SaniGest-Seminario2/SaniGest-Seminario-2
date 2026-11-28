package com.sanigest.sales.api.controller.sale;

import com.sanigest.sales.api.dto.options.SalesOptions;
import com.sanigest.sales.api.dto.sale.SalePageResponse;
import com.sanigest.sales.application.exception.sale.InvalidSaleQuery;
import com.sanigest.sales.application.usecase.sale.ListSales;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SaleQueryController {
  private static final Pattern DATE_FORMAT = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
  private final ListSales listSales;

  SaleQueryController(ListSales listSales) {
    this.listSales = listSales;
  }

  @GetMapping(path = "/api/v1/sales/options", produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("isAuthenticated()")
  public SalesOptions options() {
    listSales.requireReadCompany();
    return SalesOptions.defaults();
  }

  @GetMapping(path = "/api/v1/sales", produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("isAuthenticated()")
  public SalePageResponse list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String mode,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = ListSales.DEFAULT_SIZE) int size) {
    return SalePageResponse.from(
        listSales.execute(search, status, mode, date(from), date(to), page, size));
  }

  private static LocalDate date(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    if (!DATE_FORMAT.matcher(value).matches()) {
      throw new InvalidSaleQuery();
    }
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException invalid) {
      throw new InvalidSaleQuery();
    }
  }
}
