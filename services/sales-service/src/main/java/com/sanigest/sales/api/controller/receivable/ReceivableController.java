package com.sanigest.sales.api.controller.receivable;

import com.sanigest.sales.api.dto.receivable.ReceivableDetailResponse;
import com.sanigest.sales.api.dto.receivable.ReceivableListResponse;
import com.sanigest.sales.application.dto.receivable.ReceivableSearch.Filter;
import com.sanigest.sales.application.usecase.receivable.GetReceivable;
import com.sanigest.sales.application.usecase.receivable.ListReceivables;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/receivables")
@PreAuthorize("isAuthenticated()")
public class ReceivableController {
  private final GetReceivable getReceivable;

  private final ListReceivables listReceivables;

  ReceivableController(GetReceivable getReceivable, ListReceivables listReceivables) {
    this.getReceivable = getReceivable;
    this.listReceivables = listReceivables;
  }

  @GetMapping
  public ReceivableListResponse.Page page(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "ALL") Filter status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ReceivableListResponse.Page.from(listReceivables.page(search, status, page, size));
  }

  @GetMapping("/all")
  public List<ReceivableListResponse> all(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "ALL") Filter status) {
    return listReceivables.all(search, status).stream().map(ReceivableListResponse::from).toList();
  }

  @GetMapping("/{id}")
  public ReceivableDetailResponse detail(@PathVariable UUID id) {
    return ReceivableDetailResponse.from(getReceivable.execute(id));
  }
}
