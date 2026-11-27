package com.sanigest.finance.infrastructure;

import com.sanigest.finance.application.ActiveCashSummary.Amounts;
import com.sanigest.finance.application.ActiveCashSummary.Movements;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PostgresCashSummary implements Movements {
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
  private final JdbcClient jdbc;

  PostgresCashSummary(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Amounts totals(UUID companyId, UUID shiftId) {
    return jdbc.sql(
            """
            SELECT method,
                   SUM(CASE WHEN origin = 'COBRO' AND direction = 'INFLOW' THEN amount
                            WHEN origin = 'COBRO' AND original_event_id IS NOT NULL THEN -amount
                            ELSE 0.00 END) AS collections,
                   SUM(CASE direction WHEN 'INFLOW' THEN amount ELSE -amount END) AS net
            FROM finance.cash_movements
            WHERE company_id = :company AND shift_id = :shift
              AND currency = 'DOP' AND direction IS NOT NULL
            GROUP BY method
            """)
        .param("company", companyId)
        .param("shift", shiftId)
        .query(rows -> {
          BigDecimal total = ZERO;
          BigDecimal cash = ZERO;
          var other =
              new HashMap<>(
                  Map.of("CREDIT_CARD", ZERO, "DEBIT_CARD", ZERO, "TRANSFER", ZERO, "CHECK", ZERO));
          while (rows.next()) {
            String method = rows.getString("method");
            BigDecimal collections = rows.getBigDecimal("collections");
            total = total.add(collections);
            if ("CASH".equals(method)) {
              cash = rows.getBigDecimal("net");
            } else if (other.containsKey(method)) {
              other.put(method, collections);
            }
          }
          return new Amounts(total, cash, Map.copyOf(other));
        });
  }
}
