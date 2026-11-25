package com.sanigest.catalog.infrastructure.persistence.jpa;

import com.sanigest.catalog.application.dto.ClientReferences.Option;
import com.sanigest.catalog.application.dto.ClientReferences.ReceiptType;
import com.sanigest.catalog.application.port.out.persistence.ClientReferenceRepository;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcClientReferences implements ClientReferenceRepository {
  private final JdbcClient jdbc;

  JdbcClientReferences(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Option> countries() {
    return jdbc.sql("SELECT code, name FROM catalog.client_countries ORDER BY name")
        .query(Option.class)
        .list();
  }

  @Override
  public List<Option> provinces(String country) {
    return jdbc.sql(
            """
            SELECT code, name FROM catalog.client_provinces WHERE country_code = :country ORDER BY name
            """)
        .param("country", country)
        .query(Option.class)
        .list();
  }

  @Override
  public List<Option> municipalities(String country, String province) {
    return jdbc.sql(
            """
            SELECT code, name FROM catalog.client_municipalities
            WHERE country_code = :country AND province_code = :province ORDER BY name
            """)
        .param("country", country)
        .param("province", province)
        .query(Option.class)
        .list();
  }

  @Override
  public List<ReceiptType> receiptTypes() {
    return jdbc.sql(
            """
            SELECT code, name, version, active FROM catalog.client_receipt_types ORDER BY code
            """)
        .query(ReceiptType.class)
        .list();
  }

  @Override
  public boolean activeReceiptType(String code) {
    return jdbc.sql(
            """
            SELECT EXISTS (SELECT 1 FROM catalog.client_receipt_types WHERE code = :code AND active)
            """)
        .param("code", code)
        .query(Boolean.class)
        .single();
  }
}
