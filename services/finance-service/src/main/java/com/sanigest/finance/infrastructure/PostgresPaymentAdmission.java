package com.sanigest.finance.infrastructure;

import com.sanigest.finance.application.PaymentAdmission;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PostgresPaymentAdmission implements PaymentAdmission {
  private final JdbcClient jdbc;

  PostgresPaymentAdmission(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<ActiveShift> find(UUID companyId, UUID operatorId) {
    return jdbc.sql(
            """
            SELECT shift.id, account.method, account.account_id
            FROM finance.cash_shifts shift
            LEFT JOIN finance.shift_payment_accounts account
              ON account.company_id = shift.company_id AND account.shift_id = shift.id
            WHERE shift.company_id = :company AND shift.operator_id = :operator
              AND shift.closed_at IS NULL
            """)
        .param("company", companyId)
        .param("operator", operatorId)
        .query(rows -> {
          if (!rows.next()) {
            return Optional.empty();
          }
          UUID shiftId = rows.getObject("id", UUID.class);
          Map<String, UUID> accounts = new HashMap<>();
          do {
            String method = rows.getString("method");
            if (method != null) {
              accounts.put(method, rows.getObject("account_id", UUID.class));
            }
          } while (rows.next());
          return Optional.of(new ActiveShift(shiftId, Map.copyOf(accounts)));
        });
  }
}
