package com.sanigest.finance.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sanigest.finance.application.dto.ManualMovement.Category;
import com.sanigest.finance.application.dto.ManualMovement.Document;
import com.sanigest.finance.application.dto.ManualMovement.Method;
import com.sanigest.finance.application.dto.ManualMovement.Request;
import com.sanigest.finance.application.dto.ManualMovement.Type;
import com.sanigest.finance.application.exception.ManualMovementConflict;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

class FinanceJdbcTest {
  private final JdbcClient jdbc = mock(JdbcClient.class);
  private final JdbcClient.StatementSpec sql = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
  private final UUID company = UUID.randomUUID();

  FinanceJdbcTest() {
    when(jdbc.sql(anyString())).thenReturn(sql);
  }

  @Test
  void mapsShiftAccountsAndKeepsCompanyAndOperatorScoped() throws Exception {
    var rows = mock(ResultSet.class);
    var operator = UUID.randomUUID();
    var shift = UUID.randomUUID();
    var account = UUID.randomUUID();
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getObject("id", UUID.class)).thenReturn(shift);
    when(rows.getString("method")).thenReturn("CASH", (String) null);
    when(rows.getObject("account_id", UUID.class)).thenReturn(account);
    respondWith(rows);

    var result = new PostgresPaymentAdmission(jdbc).find(company, operator).orElseThrow();
    assertEquals(shift, result.shiftId());
    assertEquals(Map.of("CASH", account), result.accountsByMethod());
    verify(sql).param("company", company);
    verify(sql).param("operator", operator);
  }

  @Test
  void aggregatesCollectionMethodsAndKeepsNetCashSeparate() throws Exception {
    var rows = mock(ResultSet.class);
    var shift = UUID.randomUUID();
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getString("method")).thenReturn("CASH", "TRANSFER");
    when(rows.getBigDecimal("collections"))
        .thenReturn(new BigDecimal("10.00"), new BigDecimal("5.00"));
    when(rows.getBigDecimal("net")).thenReturn(new BigDecimal("3.00"));
    respondWith(rows);

    var result = new PostgresCashSummary(jdbc).totals(company, shift);
    assertEquals(new BigDecimal("15.00"), result.totalCollections());
    assertEquals(new BigDecimal("3.00"), result.netCash());
    assertEquals(new BigDecimal("5.00"), result.otherMethods().get("TRANSFER"));
    assertEquals(new BigDecimal("0.00"), result.otherMethods().get("CHECK"));
    verify(sql).param("company", company);
    verify(sql).param("shift", shift);
  }

  @Test
  void insertsBothTransferLegsAndUsesExistingOperationOnRetry() {
    var store = new PostgresManualCashMovements(jdbc, JsonMapper.builder().build());
    UUID actor = UUID.randomUUID();
    UUID source = new UUID(0, 1);
    UUID destination = new UUID(0, 2);
    var transfer =
        new Request(
            Type.INTERNAL_TRANSFER,
            LocalDate.of(2026, 11, 28),
            Category.OTHER_INCOME,
            Method.TRANSFER,
            "Entidad",
            BigDecimal.TEN,
            "Nota",
            "Concepto",
            destination,
            source,
            "REF");
    when(sql.update()).thenReturn(1);
    var document = new Document("application/pdf", new byte[] {1});
    UUID operation = store.save(company, actor, "key", "hash", transfer, document);
    verify(sql, times(2)).param("operation", operation);
    verify(sql).param("direction", "INFLOW");
    verify(sql).param("direction", "OUTFLOW");

    when(sql.update()).thenReturn(0);
    @SuppressWarnings("unchecked")
    JdbcClient.MappedQuerySpec<UUID> existing = mock(JdbcClient.MappedQuerySpec.class);
    when(sql.query(UUID.class)).thenReturn(existing);
    when(existing.optional()).thenReturn(Optional.of(operation), Optional.empty());
    assertEquals(operation, store.save(company, actor, "key", "hash", transfer, document));
    assertThrows(
        ManualMovementConflict.class,
        () -> store.save(company, actor, "key", "changed", transfer, null));
    verify(sql, times(2)).param("operation", operation);
  }

  private void respondWith(ResultSet rows) {
    when(sql.query(any(ResultSetExtractor.class)))
        .thenAnswer(call -> call.<ResultSetExtractor<?>>getArgument(0).extractData(rows));
  }
}
