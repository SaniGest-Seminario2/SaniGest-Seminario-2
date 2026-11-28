package com.sanigest.company.application.usecase.profile;

import com.sanigest.company.application.dto.profile.CreateCompanyCommand;
import com.sanigest.company.application.exception.access.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.profile.CompanyConflictException;
import com.sanigest.company.application.port.out.persistence.profile.CompanyRegistrationStore;
import com.sanigest.company.domain.enums.team.CompanyRole;
import com.sanigest.company.domain.enums.team.MembershipStatus;
import com.sanigest.company.domain.model.profile.Company;
import com.sanigest.company.domain.model.team.Membership;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateCompanyUseCase {
  private final CompanyRegistrationStore registrations;

  private final boolean multipleMembershipsEnabled;

  public CreateCompanyUseCase(
      CompanyRegistrationStore registrations,
      @Value("${sanigest.company.multiple-memberships-enabled:false}")
          boolean multipleMembershipsEnabled) {
    this.registrations = registrations;
    this.multipleMembershipsEnabled = multipleMembershipsEnabled;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Company create(UUID userId, String key, CreateCompanyCommand command) {
    if (key == null
        || key.isBlank()
        || key.length() > 128
        || key.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("Invalid idempotency key");
    }
    String hash = hash(command);
    registrations.lockMembershipCreation(userId);
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
    if (!multipleMembershipsEnabled && registrations.hasMembership(userId)) {
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
    var owner =
        new Membership(
            UUID.randomUUID(), company.id(), userId, CompanyRole.OWNER, MembershipStatus.ACTIVE);
    registrations.insert(key, hash, company, owner);
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
