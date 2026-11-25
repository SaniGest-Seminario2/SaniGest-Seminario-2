package com.sanigest.catalog.infrastructure.persistence.jpa;

import com.sanigest.companyaccess.AuthenticatedActor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class CatalogHistoryActor {
  private final JdbcClient jdbc;

  CatalogHistoryActor(JdbcClient jdbc) {
    this.jdbc = jdbc;
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
