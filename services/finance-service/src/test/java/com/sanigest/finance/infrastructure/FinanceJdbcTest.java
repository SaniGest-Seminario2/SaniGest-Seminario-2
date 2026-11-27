package com.sanigest.finance.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.simple.JdbcClient;

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

  private void respondWith(ResultSet rows) {
    when(sql.query(any(ResultSetExtractor.class)))
        .thenAnswer(call -> call.<ResultSetExtractor<?>>getArgument(0).extractData(rows));
  }
}
