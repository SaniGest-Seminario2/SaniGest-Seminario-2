package com.sanigest.company.application;

import com.sanigest.company.domain.model.CompanyAuditEvent;
import com.sanigest.company.domain.model.CompanyAuditEvent.Operation;
import com.sanigest.company.domain.model.CompanyAuditEvent.Result;
import com.sanigest.company.domain.model.CompanyRole;
import com.sanigest.company.domain.model.Membership;
import com.sanigest.company.domain.model.MembershipStatus;
import com.sanigest.company.domain.port.CompanyAuditRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyAuditRecorder {
  private final CompanyAuditRepository repository;
  private final Clock clock;

  CompanyAuditRecorder(CompanyAuditRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void succeeded(
      UUID actorId,
      Membership member,
      Operation operation,
      CompanyRole previousRole,
      CompanyRole newRole,
      MembershipStatus previousStatus,
      MembershipStatus newStatus) {
    repository.append(
        new CompanyAuditEvent(
            UUID.randomUUID(),
            member.companyId(),
            actorId,
            member.userId(),
            operation,
            clock.instant(),
            Result.SUCCESS,
            previousRole,
            newRole,
            previousStatus,
            newStatus));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void rejected(UUID companyId, UUID actorId, UUID targetId, Operation operation) {
    repository.append(
        new CompanyAuditEvent(
            UUID.randomUUID(),
            companyId,
            actorId,
            targetId,
            operation,
            clock.instant(),
            Result.REJECTED,
            null,
            null,
            null,
            null));
  }
}
