package com.sanigest.inventory.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sanigest.inventory.api.controller.InventoryController;
import com.sanigest.inventory.api.controller.WarehouseController;
import com.sanigest.inventory.api.error.InventoryErrors;
import com.sanigest.inventory.application.dto.InventoryQueries;
import com.sanigest.inventory.application.port.out.integration.CatalogInventoryClient;
import com.sanigest.inventory.application.port.out.integration.InventoryAccess;
import com.sanigest.inventory.application.port.out.persistence.StockLedger;
import com.sanigest.inventory.application.port.out.persistence.Warehouses;
import com.sanigest.inventory.application.usecase.InventoryReads;
import com.sanigest.inventory.application.usecase.StockOperations;
import com.sanigest.inventory.application.usecase.WarehouseOperations;
import com.sanigest.inventory.domain.exception.InventoryViolation;
import com.sanigest.inventory.domain.model.StockBalance;
import com.sanigest.inventory.domain.model.StockConfiguration;
import com.sanigest.inventory.domain.model.StockKey;
import com.sanigest.inventory.domain.model.StockMovement;
import com.sanigest.inventory.domain.model.StockPosition;
import com.sanigest.inventory.domain.model.Warehouse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class InventoryWebTest {
  private static final String WAREHOUSES = "/api/v1/warehouses";
  private static final String STOCK = "/api/v1/inventory/stock";
  private static final String ADJUST = "/api/v1/inventory/adjustments";
  private static final String COMPANY = "X-Company-Id";
  private final UUID company = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID warehouse = UUID.randomUUID();
  private final UUID item = UUID.randomUUID();
  private final StockKey key = new StockKey(company, item, warehouse);
  private final Warehouses warehouses = mock(Warehouses.class);
  private final StockLedger ledger = mock(StockLedger.class);
  private final InventoryAccess access = mock(InventoryAccess.class);
  private final StockOperations stock = mock(StockOperations.class);
  private final WarehouseOperations storage = new WarehouseOperations(warehouses);
  private final Warehouse warehouseModel =
      new Warehouse(
          warehouse,
          company,
          "MAIN",
          "Main",
          "Office",
          Warehouse.Status.ACTIVE,
          Instant.EPOCH,
          Instant.EPOCH,
          0);
  private final StockPosition position =
      new StockPosition(
          key,
          new StockConfiguration(true, new BigDecimal("2"), null, null),
          new StockBalance(new BigDecimal("2.5"), new BigDecimal("0.5")),
          true,
          "KG",
          true,
          "SKU",
          "Product",
          Instant.EPOCH,
          Instant.EPOCH,
          Instant.EPOCH,
          0,
          1);
  private MockMvc mvc;

  private final CatalogInventoryClient catalog = mock(CatalogInventoryClient.class);

  @BeforeEach
  void configure() throws Exception {
    var reads = new InventoryReads(storage, ledger, catalog);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new WarehouseController(access, storage),
                new InventoryController(access, stock, reads))
            .setControllerAdvice(new InventoryErrors())
            .setMessageConverters(
                new JacksonJsonHttpMessageConverter(
                    JsonMapper.builder()
                        .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build()))
            .build();
    when(catalog.find(company, item))
        .thenReturn(
            new CatalogInventoryClient.Item(item, "PRODUCT", true, "KG", true, "SKU", "Article"));
    when(access.authorize(eq(company), any())).thenReturn(actor);
    when(warehouses.find(company, warehouse, false)).thenReturn(Optional.of(warehouseModel));
    when(warehouses.find(company, warehouse, true)).thenReturn(Optional.of(warehouseModel));
  }

  @Test
  void createsGetsListsAndPatchesWarehouses() throws Exception {
    when(warehouses.insert(any())).thenReturn(true);
    when(warehouses.list(eq(company), any()))
        .thenReturn(new InventoryQueries.Page<>(List.of(warehouseModel), 1, 0, 20));
    mvc.perform(
            post(WAREHOUSES)
                .header(COMPANY, company)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\" alm-2 \",\"name\":\"Warehouse\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("ALM-2"));
    mvc.perform(get(WAREHOUSES + "/" + warehouse).header(COMPANY, company))
        .andExpect(status().isOk());
    mvc.perform(get(WAREHOUSES).header(COMPANY, company))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalPages").value(1));
    mvc.perform(
            patch(WAREHOUSES + "/" + warehouse)
                .header(COMPANY, company)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"location\":null,\"status\":\"INACTIVE\",\"revision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.location").isEmpty())
        .andExpect(jsonPath("$.status").value("INACTIVE"))
        .andExpect(jsonPath("$.name").value("Main"));
    mvc.perform(
            patch(WAREHOUSES + "/" + warehouse)
                .header(COMPANY, company)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\",\"code\":\"SECOND\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.location").value("Office"));
  }

  @Test
  void stockAndMovementsUseDecimalStringsAndDistinguishAbsentPosition() throws Exception {
    String path = STOCK + "/" + item + "/" + warehouse;
    mvc.perform(get(path).header(COMPANY, company))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availability").value("NOT_TRACKED"))
        .andExpect(jsonPath("$.warehouseName").value("Main"))
        .andExpect(jsonPath("$.onHand").isEmpty());
    when(catalog.find(company, item))
        .thenReturn(
            new CatalogInventoryClient.Item(
                item, "SERVICE", true, "UNIT", false, null, "Consulting"));
    mvc.perform(get(path).header(COMPANY, company))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availability").value("NOT_APPLICABLE"))
        .andExpect(jsonPath("$.itemName").value("Consulting"))
        .andExpect(jsonPath("$.available").isEmpty());
    when(ledger.find(key)).thenReturn(Optional.of(position));
    when(stock.configure(eq(key), eq(actor), eq("start"), any())).thenReturn(position);
    var movement =
        new StockMovement(
            UUID.randomUUID(),
            key,
            StockMovement.Type.MANUAL_ADJUSTMENT,
            StockMovement.Direction.IN,
            new BigDecimal("2"),
            "KG",
            StockMovement.Origin.MANUAL_ADJUSTMENT,
            UUID.randomUUID(),
            UUID.randomUUID(),
            actor,
            "Reason",
            Instant.EPOCH,
            Instant.EPOCH,
            1,
            new BigDecimal("2.500"));
    when(stock.adjust(eq(key), eq(actor), eq("start"), any())).thenReturn(movement);
    when(ledger.list(eq(company), any()))
        .thenReturn(
            new InventoryQueries.Page<>(
                List.of(new InventoryQueries.StockEntry(position, "Main")), 1, 0, 20));
    when(ledger.movements(eq(company), any()))
        .thenReturn(new InventoryQueries.Page<>(List.of(movement), 1, 0, 20));
    when(ledger.summary(company, warehouse))
        .thenReturn(new InventoryQueries.Summary(1, 1, 0, 1, 0, 0));
    var detail =
        mvc.perform(get(path).header(COMPANY, company))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.onHand").value("2.500"))
            .andExpect(jsonPath("$.availability").value("LOW"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var configured =
        mvc.perform(
                put(path)
                    .header(COMPANY, company)
                    .header("Idempotency-Key", "start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"trackingEnabled\":true,\"initialQuantity\":\"2.500\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.warehouseName").value("Main"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    org.assertj.core.api.Assertions.assertThat(JsonMapper.shared().readTree(configured))
        .isEqualTo(JsonMapper.shared().readTree(detail));
    mvc.perform(
            post(ADJUST)
                .header(COMPANY, company)
                .header("Idempotency-Key", "start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustment("\"2.000\"", "Reason")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.quantity").value("2.000"));
    mvc.perform(get(STOCK + "?availability=LOW&order=ASC&size=20").header(COMPANY, company))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get("/api/v1/inventory/movements?direction=IN").header(COMPANY, company))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].actorId").value(actor.toString()));
    mvc.perform(get("/api/v1/inventory/summary?warehouseId=" + warehouse).header(COMPANY, company))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lowPositions").value(1));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"name\":\"x\"}",
        "{\"code\":\"A\",\"code\":\"B\",\"name\":\"x\"}",
        "{\"code\":\"A\",\"name\":\"x\",\"companyId\":\"untrusted\"}"
      })
  void rejectsMalformedWarehousePayload(String body) throws Exception {
    mvc.perform(
            post(WAREHOUSES)
                .header(COMPANY, company)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"location\":42}",
        "{\"status\":\"UNKNOWN\"}",
        "{\"revision\":-1}",
        "{\"code\":null}",
        "{\"status\":null}",
        "{}",
        "{\"onHand\":12}"
      })
  void validatesPatchPresenceAndTypes(String body) throws Exception {
    mvc.perform(
            patch(WAREHOUSES + "/" + warehouse)
                .header(COMPANY, company)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void validatesContextKeysAndFilters() throws Exception {
    mvc.perform(get(STOCK))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("COMPANY_CONTEXT_REQUIRED"));
    mvc.perform(get(STOCK).header(COMPANY, company, company)).andExpect(status().isBadRequest());
    mvc.perform(get(STOCK).header(COMPANY, "invalid")).andExpect(status().isBadRequest());
    mvc.perform(get(STOCK + "?size=0").header(COMPANY, company)).andExpect(status().isBadRequest());
    mvc.perform(get(WAREHOUSES + "/uuid").header(COMPANY, company))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(ADJUST)
                .header(COMPANY, company)
                .header("Idempotency-Key", "a", "b")
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustment("\"2\"", "Reason")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_IDEMPOTENCY_KEY"));
    mvc.perform(
            post(ADJUST)
                .header(COMPANY, company)
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustment("\"2\"", " ")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(ADJUST)
                .header(COMPANY, company)
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustment("2.50", "Reason")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void mapsWarehouseConflictsAndDependencyFailures() throws Exception {
    mvc.perform(
            post(WAREHOUSES)
                .header(COMPANY, company)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"main\",\"name\":\"x\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_CODE_CONFLICT"));
    when(warehouses.hasStock(company, warehouse)).thenReturn(true);
    mvc.perform(
            patch(WAREHOUSES + "/" + warehouse)
                .header(COMPANY, company)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}"))
        .andExpect(status().isConflict());
    mvc.perform(
            patch(WAREHOUSES + "/" + warehouse)
                .header(COMPANY, company)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":99}"))
        .andExpect(status().isConflict());
    when(access.authorize(company, InventoryAccess.Operation.READ))
        .thenThrow(
            new InventoryViolation(
                InventoryViolation.Kind.UNAVAILABLE, "INVENTORY_AUTHORIZATION_UNAVAILABLE"));
    mvc.perform(get(STOCK).header(COMPANY, company)).andExpect(status().isServiceUnavailable());
  }

  private String adjustment(String amount, String reason) {
    return """
    {"catalogItemId":"%s","warehouseId":"%s","direction":"IN","quantity":%s,"reason":"%s"}
    """
        .formatted(item, warehouse, amount, reason);
  }
}
