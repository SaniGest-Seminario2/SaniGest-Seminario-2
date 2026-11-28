package com.sanigest.company.infrastructure.persistence.jdbc.profile;

import com.sanigest.company.application.exception.profile.CompanyConflictException;
import com.sanigest.company.application.port.out.persistence.profile.CompanyRepository;
import com.sanigest.company.domain.model.profile.Company;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCompanyRepository implements CompanyRepository {
  private static final String SELECT_COMPANY =
      "SELECT id, name, business_name, rnc, email, phone, location, currency, status FROM companies WHERE id = ?";

  private final JdbcTemplate jdbc;

  JdbcCompanyRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Company> findById(UUID id) {
    return find(id, SELECT_COMPANY);
  }

  @Override
  public Optional<Company> findByIdForUpdate(UUID id) {
    return find(id, SELECT_COMPANY + " FOR UPDATE");
  }

  private Optional<Company> find(UUID id, String sql) {
    return DataAccessUtils.optionalResult(
        jdbc.query(
            sql,
            (rs, rowNum) ->
                new Company(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("business_name"),
                    rs.getString("rnc"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("location"),
                    rs.getString("currency"),
                    rs.getString("status")),
            id));
  }

  @Override
  public Instant updateProfile(Company company) {
    try {
      return jdbc.queryForObject(
          """
          UPDATE companies
          SET business_name = ?, email = ?, phone = ?, location = ?, rnc = ?,
              updated_at = GREATEST(clock_timestamp(), updated_at + interval '1 microsecond')
          WHERE id = ?
          RETURNING updated_at
          """,
          (rs, rowNum) -> rs.getObject(1, OffsetDateTime.class).toInstant(),
          company.businessName(),
          company.email(),
          company.phone(),
          company.location(),
          company.rnc(),
          company.id());
    } catch (DuplicateKeyException e) {
      throw new CompanyConflictException("Company profile conflicts with existing data", e);
    }
  }
}
