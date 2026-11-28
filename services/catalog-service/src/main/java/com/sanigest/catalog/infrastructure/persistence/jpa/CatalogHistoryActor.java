package com.sanigest.catalog.infrastructure.persistence.jpa;

import com.sanigest.companyaccess.AuthenticatedActor;
import com.sanigest.security.RequestCorrelation;
import java.sql.Types;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class CatalogHistoryActor {
  private final JdbcClient jdbc;

  CatalogHistoryActor(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  void statusChanged(
      UUID company, String resourceType, UUID resource, String before, String after) {
    if (before.equals(after)) {
      return;
    }
    var actor = AuthenticatedActor.userId().orElse(null);
    jdbc.sql(
            """
            INSERT INTO catalog.audit_log
                (id, company_id, actor_id, operation, resource_type, resource_id,
                 previous_state, state, correlation_id)
            VALUES (:id, :company, :actor, 'STATUS_CHANGED', :resourceType, :resource,
                    :before, :after, :correlation)
            """)
        .param("id", UUID.randomUUID())
        .param("company", company)
        .param("actor", actor, Types.OTHER)
        .param("resourceType", resourceType)
        .param("resource", resource)
        .param("before", before)
        .param("after", after)
        .param("correlation", RequestCorrelation.current())
        .update();
  }

  void bind() {
    AuthenticatedActor.userId()
        .ifPresent(actor ->
            jdbc.sql("SELECT set_config('sanigest.catalog_actor', :actor, true)")
                .param("actor", actor.toString())
                .query(String.class)
                .single());
  }
}
