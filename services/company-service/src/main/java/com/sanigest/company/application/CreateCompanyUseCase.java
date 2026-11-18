package com.sanigest.company.application;

import com.sanigest.company.api.CompanyController.CompanyRequest;
import com.sanigest.company.api.CompanyController.CompanyResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateCompanyUseCase {
  private final JdbcTemplate jdbc;

  public CreateCompanyUseCase(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public CompanyResponse create(UUID userId, String key, CompanyRequest r) {
    if (key == null || key.isBlank() || key.length() > 128) {
      throw new IllegalArgumentException("Invalid idempotency key");
    }
    String hash = hash(r);
    var prior =
        jdbc.query(
            "SELECT request_hash, company_id FROM company_registration_idempotency "
                + "WHERE user_id=? AND idempotency_key=?",
            rs -> rs.next() ? new Prior(rs.getString(1), rs.getObject(2, UUID.class)) : null,
            userId,
            key);
    if (prior != null) {
      if (!prior.hash.equals(hash)) {
        throw new ConflictException("Idempotency key was already used with another request");
      }
      return response(prior.companyId);
    }
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM company_memberships WHERE user_id=?", Integer.class, userId)
        > 0) {
      throw new ConflictException("User already belongs to a company");
    }
    UUID companyId = UUID.randomUUID();
    UUID membershipId = UUID.randomUUID();
    try {
      jdbc.update(
          "INSERT INTO companies (id,name,business_name,rnc,email,phone,location,currency,status) "
              + "VALUES (?,?,?,?,?,?,?,'DOP','ACTIVE')",
          companyId,
          r.companyName(),
          r.businessName(),
          blankToNull(r.rnc()),
          r.email(),
          r.phone(),
          r.location());
      jdbc.update(
          "INSERT INTO company_memberships (id,company_id,user_id,role,status) VALUES (?,?,?,'OWNER','ACTIVE')",
          membershipId,
          companyId,
          userId);
      jdbc.update(
          "INSERT INTO company_registration_idempotency "
              + "(user_id,idempotency_key,request_hash,company_id) VALUES (?,?,?,?)",
          userId,
          key,
          hash,
          companyId);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("Company registration conflicts with existing data");
    }
    return response(companyId);
  }

  private CompanyResponse response(UUID id) {
    return jdbc.queryForObject(
        "SELECT id,name,business_name,rnc,email,phone,location,currency,status FROM companies WHERE id=?",
        (rs, n) ->
            new CompanyResponse(
                rs.getObject(1, UUID.class),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getString(7),
                rs.getString(8),
                rs.getString(9)),
        id);
  }

  private String hash(CompanyRequest r) {
    try {
      String body =
          String.join(
              "\u0000",
              String.valueOf(r.companyName()),
              String.valueOf(r.businessName()),
              String.valueOf(r.rnc()),
              String.valueOf(r.email()),
              String.valueOf(r.phone()),
              String.valueOf(r.location()),
              String.valueOf(r.ownerId()),
              String.valueOf(r.userId()));
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }

  record Prior(String hash, UUID companyId) {}

  public static class ConflictException extends RuntimeException {
    public ConflictException(String m) {
      super(m);
    }
  }
}
