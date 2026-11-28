package com.sanigest.web.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sanigest.web.audit.AuditPage.Entry;
import com.sanigest.web.audit.AuditPage.Filters;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class JdbcAuditReaderTest {
  private static final Instant WHEN = Instant.parse("2026-11-28T12:00:00Z");
  private static final UUID LOW_ID = new UUID(0, 1);
  private static final UUID HIGH_ID = new UUID(0, 2);
  private final UUID company = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID resource = UUID.randomUUID();
  private SingleConnectionDataSource database;
  private JdbcTemplate jdbc;
  private JdbcAuditReader reader;

  @BeforeEach
  void setUp() {
    database =
        new SingleConnectionDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL", true);
    jdbc = new JdbcTemplate(database);
    jdbc.execute(
        """
        CREATE TABLE audit_entries (
          id uuid, company_id uuid, actor_id uuid, operation text, resource_type text,
          resource_id uuid, occurred_at timestamp with time zone, result text,
          previous_state text, state text, correlation_id text
        )
        """);
    reader = new JdbcAuditReader(jdbc, "audit_entries");
  }

  @AfterEach
  void close() {
    database.destroy();
  }

  @Test
  void pagesOnlyTheSelectedCompanyWithStableOrder() {
    insert(LOW_ID, company, WHEN);
    insert(HIGH_ID, company, WHEN);
    insert(UUID.randomUUID(), UUID.randomUUID(), WHEN.plusSeconds(1));
    var page = reader.read(company, new Filters(null, null, null, null, null, null, 0, 1));
    assertThat(page.hasNext()).isTrue();
    assertThat(page.content()).extracting(Entry::id).containsExactly(HIGH_ID);
    var next = reader.read(company, new Filters(null, null, null, null, null, null, 1, 1));
    assertThat(next.hasNext()).isFalse();
    assertThat(next.content()).extracting(Entry::id).containsExactly(LOW_ID);
  }

  @Test
  void bindsAllFiltersAndMapsTheAuditMetadata() {
    insert(LOW_ID, company, WHEN);
    insert(HIGH_ID, company, WHEN.minusSeconds(1));
    var query = new Filters(WHEN, WHEN, actor, "EDIT", "ITEM", resource, 0, 10);
    assertThat(reader.read(company, query).content())
        .containsExactly(
            new Entry(LOW_ID, actor, "EDIT", "ITEM", resource, WHEN, "SUCCESS", "A", "B", "trace"));
    var unmatched = new Filters(WHEN, WHEN, actor, "OTHER", "ITEM", resource, 0, 10);
    assertThat(reader.read(company, unmatched).content()).isEmpty();
  }

  @Test
  void validatesBoundsAndUsesALongOffset() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Filters(null, null, null, null, null, null, -1, 1));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Filters(null, null, null, null, null, null, 0, 0));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Filters(null, null, null, null, null, null, 0, 101));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Filters(WHEN, WHEN.minusSeconds(1), null, null, null, null, 0, 1));
    assertThat(new Filters(null, null, null, null, null, null, Integer.MAX_VALUE, 100).offset())
        .isEqualTo(100L * Integer.MAX_VALUE);
  }

  private void insert(UUID id, UUID tenant, Instant when) {
    jdbc.update(
        "INSERT INTO audit_entries VALUES (?, ?, ?, 'EDIT', 'ITEM', ?, ?, 'SUCCESS', 'A', 'B', 'trace')",
        id,
        tenant,
        actor,
        resource,
        OffsetDateTime.ofInstant(when, ZoneOffset.UTC));
  }
}
