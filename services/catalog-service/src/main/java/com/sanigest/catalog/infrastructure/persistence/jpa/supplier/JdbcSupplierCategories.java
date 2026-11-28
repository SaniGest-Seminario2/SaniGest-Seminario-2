package com.sanigest.catalog.infrastructure.persistence.jpa.supplier;

import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierCategoryRepository;
import com.sanigest.catalog.domain.enums.supplier.SupplierStatus;
import com.sanigest.catalog.domain.model.supplier.SupplierCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSupplierCategories implements SupplierCategoryRepository {
  private static final String BY_COMPANY =
      """
      SELECT id, company_id, code, name, status
      FROM catalog.supplier_categories WHERE company_id = :company
      """;
  private static final RowMapper<SupplierCategory> CATEGORY =
      (row, index) ->
          new SupplierCategory(
              row.getObject("id", UUID.class),
              row.getObject("company_id", UUID.class),
              row.getString("code"),
              row.getString("name"),
              SupplierStatus.valueOf(row.getString("status")));

  private final JdbcClient jdbc;

  JdbcSupplierCategories(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void initialize(UUID companyId) {
    jdbc.sql(
            """
            INSERT INTO catalog.supplier_categories (id, company_id, code, name)
            SELECT gen_random_uuid(), :company, code, name
            FROM (VALUES ('MATERIALS_SUPPLIES', 'Materiales y suministros'),
                         ('TECHNOLOGY_EQUIPMENT', 'Equipos tecnológicos'),
                         ('MERCHANDISE', 'Mercancías'), ('FOOD_BEVERAGES', 'Alimentos y bebidas'),
                         ('MAINTENANCE', 'Mantenimiento'),
                         ('PROFESSIONAL_SERVICES', 'Servicios profesionales'),
                         ('OTHER', 'Otros')) AS seed(code, name)
            ORDER BY code
            ON CONFLICT (company_id, code) DO NOTHING
            """)
        .param("company", companyId)
        .update();
  }

  @Override
  public List<SupplierCategory> list(UUID companyId) {
    return jdbc.sql(BY_COMPANY + " ORDER BY name, id")
        .param("company", companyId)
        .query(CATEGORY)
        .list();
  }

  @Override
  public Optional<SupplierCategory> lockForAssignment(UUID companyId, UUID id) {
    return jdbc.sql(BY_COMPANY + " AND id = :id FOR SHARE")
        .param("company", companyId)
        .param("id", id)
        .query(CATEGORY)
        .optional();
  }
}
