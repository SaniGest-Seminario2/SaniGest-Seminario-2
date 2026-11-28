package com.sanigest.inventory.infrastructure.persistence;

import com.sanigest.inventory.application.port.out.persistence.Idempotency;
import com.sanigest.inventory.domain.exception.InventoryViolation;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
class JdbcIdempotency implements Idempotency {
  private static final String SCOPE =
      "company_id=:company AND actor_id=:actor AND operation=:operation AND key=:key";
  private final JdbcClient db;
  private final ObjectMapper json;

  JdbcIdempotency(JdbcClient db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  @Override
  public <T> Optional<T> replay(Key key, String fingerprint, Class<T> type) {
    return bind("SELECT fingerprint, result FROM inventory.idempotency_keys WHERE " + SCOPE, key)
        .query((row, index) -> {
          if (!fingerprint.equals(row.getString("fingerprint"))) {
            throw InventoryViolation.conflict("IDEMPOTENCY_CONFLICT");
          }
          return json.readValue(row.getString("result"), type);
        })
        .optional();
  }

  @Override
  public boolean claim(Key key, String fingerprint) {
    return bind(
                """
                INSERT INTO inventory.idempotency_keys (company_id,actor_id,operation,key,fingerprint)
                VALUES (:company,:actor,:operation,:key,:fingerprint) ON CONFLICT DO NOTHING
                """,
                key)
            .param("fingerprint", fingerprint)
            .update()
        == 1;
  }

  @Override
  public void complete(Key key, Object result) {
    if (bind(
                "UPDATE inventory.idempotency_keys SET result=CAST(:result AS jsonb) WHERE "
                    + SCOPE,
                key)
            .param("result", json.writeValueAsString(result))
            .update()
        != 1) {
      throw new IllegalStateException("Missing inventory idempotency claim");
    }
  }

  private JdbcClient.StatementSpec bind(String sql, Key key) {
    return db.sql(sql)
        .param("company", key.company())
        .param("actor", key.actor())
        .param("operation", key.operation().name())
        .param("key", key.value());
  }
}
