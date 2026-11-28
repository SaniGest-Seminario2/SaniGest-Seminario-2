package com.sanigest.sales.api.controller.sale;

import com.sanigest.sales.api.dto.options.SalesOptions;
import com.sanigest.sales.api.dto.sale.SalePageResponse;
import com.sanigest.sales.api.dto.sale.SaleSummaryResponse;
import com.sanigest.sales.application.exception.sale.InvalidSaleQuery;
import com.sanigest.sales.application.usecase.sale.ListSales;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SaleQueryController {
  private static final Pattern DATE_FORMAT = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
  private static final String FILTERS =
      "Search by customer or invoice number. Filters use inclusive YYYY-MM-DD dates and mode ALL, CASH or CREDIT.";
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
  @Operation(description = FILTERS)
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

  @GetMapping(path = "/api/v1/sales/all", produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "List up to 1000 sales", description = FILTERS)
  @ApiResponse(responseCode = "200", description = "Matching sales", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "422", description = "SALE_LIST_LIMIT_EXCEEDED")
  public List<SalePageResponse.Item> all(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String mode,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    return listSales.all(search, status, mode, date(from), date(to)).stream()
        .map(SalePageResponse.Item::from)
        .toList();
  }

  @GetMapping(path = "/api/v1/sales/summary", produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Summarize matching sales",
      description =
          FILTERS
              + " Cancelled sales are excluded. Average ticket is rounded to two decimals (HALF_UP)"
              + " and is null when no sales match.")
  public SaleSummaryResponse summary(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String mode,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    return SaleSummaryResponse.from(listSales.summary(search, status, mode, date(from), date(to)));
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
