package com.sanigest.catalog.infrastructure.persistence.jpa;

import com.sanigest.catalog.application.port.out.persistence.ClientCategoryRepository;
import com.sanigest.catalog.domain.enums.ClientStatus;
import com.sanigest.catalog.domain.model.ClientCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcClientCategories implements ClientCategoryRepository {
  private static final String BY_COMPANY =
      """
      SELECT id, company_id, code, name, status
      FROM catalog.client_categories WHERE company_id = :company
      """;
  private static final RowMapper<ClientCategory> CATEGORY =
      (row, index) ->
          new ClientCategory(
              row.getObject("id", UUID.class),
              row.getObject("company_id", UUID.class),
              row.getString("code"),
              row.getString("name"),
              ClientStatus.valueOf(row.getString("status")));

  private final JdbcClient jdbc;

  JdbcClientCategories(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void initialize(UUID companyId) {
    jdbc.sql(
            """
            INSERT INTO catalog.client_categories (id, company_id, code, name)
            SELECT gen_random_uuid(), :company, code, name
            FROM (VALUES ('REGULAR', 'Regular'), ('FREQUENT', 'Frecuente'),
                         ('WHOLESALE', 'Mayorista'), ('CORPORATE', 'Corporativo')) AS seed(code, name)
            ORDER BY code
            ON CONFLICT (company_id, code) DO NOTHING
            """)
        .param("company", companyId)
        .update();
  }

  @Override
  public List<ClientCategory> list(UUID companyId) {
    return jdbc.sql(BY_COMPANY + " ORDER BY name, id")
        .param("company", companyId)
        .query(CATEGORY)
        .list();
  }

  @Override
  public UUID defaultId(UUID companyId) {
    return jdbc.sql(
            """
            SELECT id FROM catalog.client_categories WHERE company_id = :company AND code = :code
            """)
        .param("company", companyId)
        .param("code", ClientCategory.DEFAULT_CODE)
        .query(UUID.class)
        .single();
  }

  @Override
  public Optional<ClientCategory> lockForAssignment(UUID companyId, UUID id) {
    return jdbc.sql(BY_COMPANY + " AND id = :id FOR SHARE")
        .param("company", companyId)
        .param("id", id)
        .query(CATEGORY)
        .optional();
  }

  @Override
  public Optional<ClientCategory> lockForEdit(UUID companyId, UUID id) {
    return jdbc.sql(BY_COMPANY + " AND id = :id FOR UPDATE")
        .param("company", companyId)
        .param("id", id)
        .query(CATEGORY)
        .optional();
  }

  @Override
  public boolean insert(ClientCategory category) {
    return jdbc.sql(
                """
                INSERT INTO catalog.client_categories (id, company_id, code, name)
                VALUES (:id, :company, :code, :name)
                ON CONFLICT (company_id, code) DO NOTHING
                """)
            .param("id", category.id())
            .param("company", category.companyId())
            .param("code", category.code())
            .param("name", category.name())
            .update()
        == 1;
  }

  @Override
  public void update(ClientCategory category) {
    jdbc.sql(
            """
            UPDATE catalog.client_categories SET name = :name, status = :status
            WHERE company_id = :company AND id = :id
            """)
        .param("name", category.name())
        .param("status", category.status().name())
        .param("company", category.companyId())
        .param("id", category.id())
        .update();
  }
}
