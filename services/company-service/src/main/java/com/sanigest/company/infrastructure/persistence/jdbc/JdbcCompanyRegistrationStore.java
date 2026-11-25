package com.sanigest.company.infrastructure.persistence.jdbc;

import com.sanigest.company.application.dto.CompanyRegistration;
import com.sanigest.company.application.exception.CompanyConflictException;
import com.sanigest.company.application.port.out.persistence.CompanyRegistrationStore;
import com.sanigest.company.application.port.out.persistence.CompanyRepository;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.domain.model.Company;
import com.sanigest.company.domain.model.Membership;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCompanyRegistrationStore implements CompanyRegistrationStore {
  private final JdbcTemplate jdbc;
  private final CompanyRepository companies;
  private final MembershipRepository memberships;

  JdbcCompanyRegistrationStore(
      JdbcTemplate jdbc, CompanyRepository companies, MembershipRepository memberships) {
    this.jdbc = jdbc;
    this.companies = companies;
    this.memberships = memberships;
  }

  @Override
  public void lockMembershipCreation(UUID userId) {
    memberships.lockMembershipCreation(userId);
  }

  @Override
  public Optional<CompanyRegistration> find(UUID userId, String key) {
    return DataAccessUtils.optionalResult(
        jdbc.query(
            """
            SELECT request_hash, company_id FROM company_registration_idempotency
            WHERE user_id = ? AND idempotency_key = ?
            """,
            (rs, row) -> new CompanyRegistration(rs.getString(1), rs.getObject(2, UUID.class)),
            userId,
            key));
  }

  @Override
  public boolean lockActiveMembership(UUID userId, UUID companyId) {
    return memberships.lockActiveMembership(companyId, userId);
  }

  @Override
  public boolean hasMembership(UUID userId) {
    return memberships.existsByUserId(userId);
  }

  @Override
  public Company loadCompany(UUID companyId) {
    return companies.findById(companyId).orElseThrow();
  }

  @Override
  public void insert(String key, String requestHash, Company company, Membership owner) {
    try {
      jdbc.update(
          """
          INSERT INTO companies (id, name, business_name, rnc, email, phone, location, currency, status)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          company.id(),
          company.name(),
          company.businessName(),
          company.rnc(),
          company.email(),
          company.phone(),
          company.location(),
          company.currency(),
          company.status());
      memberships.insert(owner);
      jdbc.update(
          """
          INSERT INTO company_registration_idempotency
            (user_id, idempotency_key, request_hash, company_id) VALUES (?, ?, ?, ?)
          """,
          owner.userId(),
          key,
          requestHash,
          company.id());
    } catch (DataIntegrityViolationException e) {
      throw new CompanyConflictException("Company registration conflicts with existing data", e);
    }
  }
}
