package com.sanigest.company.application.usecase.audit;

import com.sanigest.company.application.port.out.persistence.CompanyAuditRepository;
import com.sanigest.company.domain.enums.AuditOperation;
import com.sanigest.company.domain.enums.AuditResult;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import com.sanigest.company.domain.model.CompanyAuditEvent;
import com.sanigest.company.domain.model.Membership;
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
      AuditOperation operation,
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
            AuditResult.SUCCESS,
            previousRole,
            newRole,
            previousStatus,
            newStatus));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void rejected(UUID companyId, UUID actorId, UUID targetId, AuditOperation operation) {
    repository.append(
        new CompanyAuditEvent(
            UUID.randomUUID(),
            companyId,
            actorId,
            targetId,
            operation,
            clock.instant(),
            AuditResult.REJECTED,
            null,
            null,
            null,
            null));
  }
}
