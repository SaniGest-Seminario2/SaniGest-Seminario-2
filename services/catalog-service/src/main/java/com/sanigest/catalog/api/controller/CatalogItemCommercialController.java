package com.sanigest.catalog.api.controller;

import com.sanigest.catalog.api.dto.CatalogItemDetailResponse;
import com.sanigest.catalog.application.dto.CatalogItemReferences.Unit;
import com.sanigest.catalog.application.dto.CatalogItemSummary;
import com.sanigest.catalog.application.usecase.catalogitem.CatalogItemCommercialQueries;
import com.sanigest.catalog.application.usecase.catalogitem.ListCatalogItems;
import com.sanigest.catalog.domain.model.CatalogItemCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/catalog-items")
class CatalogItemCommercialController {
  private final CatalogItemCommercialQueries commercial;
  private final ObjectMapper json;

  CatalogItemCommercialController(CatalogItemCommercialQueries commercial, ObjectMapper json) {
    this.commercial = commercial;
    this.json = json;
  }

  @GetMapping("/categories")
  List<Category> categories() {
    return commercial.categories().stream().map(Category::from).toList();
  }

  @PostMapping("/categories")
  @ResponseStatus(HttpStatus.CREATED)
  Category createCategory(@RequestBody @Valid CreateCategory request) {
    return Category.from(commercial.createCategory(request.code(), request.name()));
  }

  @PutMapping("/categories/{id}")
  Category updateCategory(@PathVariable UUID id, @RequestBody @Valid UpdateCategory request) {
    return Category.from(commercial.updateCategory(id, request.name(), request.active()));
  }

  @GetMapping("/units")
  List<Unit> units() {
    return commercial.units();
  }

  @GetMapping("/tax-classifications")
  List<Tax> taxes() {
    return commercial.taxes().stream()
        .map(tax ->
            new Tax(
                tax.code(),
                tax.name(),
                CatalogItemDetailResponse.decimal(tax.rate()),
                tax.effectiveFrom(),
                tax.effectiveUntil()))
        .toList();
  }

  @GetMapping("/summary")
  CatalogItemSummary summary() {
    return commercial.summary();
  }

  @GetMapping("/{id}/history")
  History history(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = ListCatalogItems.DEFAULT_SIZE) int size) {
    var result = commercial.history(id, page, size);
    var content =
        result.content().stream()
            .map(entry ->
                new HistoryEntry(
                    entry.id(),
                    entry.actorId(),
                    entry.operation(),
                    entry.occurredAt(),
                    parse(entry.previousValues()),
                    parse(entry.newValues())))
            .toList();
    return new History(content, page, size, result.totalElements());
  }

  private JsonNode parse(String value) {
    return value == null ? null : json.readTree(value);
  }

  record CreateCategory(
      @NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}") String code,
      @NotBlank @Size(max = 100) String name) {}

  record UpdateCategory(@NotBlank @Size(max = 100) String name, @NotNull Boolean active) {}

  record Category(UUID id, String code, String name, boolean active) {
    static Category from(CatalogItemCategory category) {
      return new Category(category.id(), category.code(), category.name(), category.active());
    }
  }

  record Tax(
      String code, String name, String rate, LocalDate effectiveFrom, LocalDate effectiveUntil) {}

  record History(List<HistoryEntry> content, int page, int size, long totalElements) {}

  record HistoryEntry(
      long id,
      UUID actorId,
      String operation,
      Instant occurredAt,
      JsonNode previousValues,
      JsonNode newValues) {}
}
