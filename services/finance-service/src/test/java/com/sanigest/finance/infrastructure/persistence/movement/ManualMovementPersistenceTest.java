package com.sanigest.finance.infrastructure.persistence.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sanigest.finance.application.dto.movement.ManualMovement.Category;
import com.sanigest.finance.application.dto.movement.ManualMovement.Document;
import com.sanigest.finance.application.dto.movement.ManualMovement.Method;
import com.sanigest.finance.application.dto.movement.ManualMovement.Request;
import com.sanigest.finance.application.dto.movement.ManualMovement.Type;
import com.sanigest.finance.application.exception.movement.ManualMovementConflict;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

class ManualMovementPersistenceTest {
  private final JdbcClient jdbc = mock(JdbcClient.class);
  private final JdbcClient.StatementSpec sql = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
  private final UUID company = UUID.randomUUID();

  ManualMovementPersistenceTest() {
    when(jdbc.sql(anyString())).thenReturn(sql);
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
}
