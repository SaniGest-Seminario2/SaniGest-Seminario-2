package com.sanigest.company.infrastructure.persistence;

import com.sanigest.company.application.GetMyCompanyUseCase.CompanyMembership;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyProfile;
import com.sanigest.company.application.GetMyCompanyUseCase.MembershipRepository;
import com.sanigest.company.domain.model.MembershipStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMembershipRepository implements MembershipRepository {
  private final JdbcTemplate jdbc;

  JdbcMembershipRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<CompanyMembership> findCompanyByUserId(UUID userId) {
    return DataAccessUtils.optionalResult(
        jdbc.query(
            """
            SELECT m.status AS membership_status, c.id, c.name, c.business_name,
                   c.rnc, c.location, c.currency, c.status
            FROM company_memberships m
            JOIN companies c ON c.id = m.company_id
            WHERE m.user_id = ?
            """,
            (rs, rowNum) ->
                new CompanyMembership(
                    MembershipStatus.valueOf(rs.getString("membership_status")),
                    new CompanyProfile(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("business_name"),
                        rs.getString("rnc"),
                        rs.getString("location"),
                        rs.getString("currency"),
                        rs.getString("status"))),
            userId));
  }
}
