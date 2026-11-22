package com.sanigest.company.application.usecase.profile;

import com.sanigest.company.application.dto.CompanyRegistration;
import com.sanigest.company.application.dto.CreateCompanyCommand;
import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.CompanyConflictException;
import com.sanigest.company.application.port.out.persistence.CompanyRegistrationStore;
import com.sanigest.company.domain.model.Company;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateCompanyUseCase {
  private final CompanyRegistrationStore registrations;

  public CreateCompanyUseCase(CompanyRegistrationStore registrations) {
    this.registrations = registrations;
  }

  @Transactional
  public Company create(UUID userId, String key, CreateCompanyCommand command) {
    if (key == null || key.isBlank() || key.length() > 128) {
      throw new IllegalArgumentException("Invalid idempotency key");
    }
    String hash = hash(command);
    var prior = registrations.find(userId, key);
    if (prior.isPresent()) {
      var registration = prior.get();
      if (!registrations.lockActiveMembership(userId, registration.companyId())) {
        throw new CompanyAccessDeniedException();
      }
      if (!registration.hash().equals(hash)) {
        throw new CompanyConflictException("Idempotency key was already used with another request");
      }
      return registrations.loadCompany(registration.companyId());
    }
    if (registrations.hasMembership(userId)) {
      throw new CompanyConflictException("User already belongs to a company");
    }

    var company =
        new Company(
            UUID.randomUUID(),
            command.companyName(),
            command.businessName(),
            blankToNull(command.rnc()),
            command.email(),
            command.phone(),
            command.location(),
            "DOP",
            "ACTIVE");
    registrations.insert(userId, key, new CompanyRegistration(hash, company.id()), company);
    return registrations.loadCompany(company.id());
  }

  private static String hash(CreateCompanyCommand command) {
    try {
      String body =
          String.join(
              "\u0000",
              String.valueOf(command.companyName()),
              String.valueOf(command.businessName()),
              String.valueOf(command.rnc()),
              String.valueOf(command.email()),
              String.valueOf(command.phone()),
              String.valueOf(command.location()),
              String.valueOf(command.ownerId()),
              String.valueOf(command.userId()));
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
}
