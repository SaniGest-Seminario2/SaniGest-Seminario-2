package com.sanigest.company.application.usecase.audit;

import com.sanigest.company.application.port.out.persistence.audit.CompanyAuditRepository;
import com.sanigest.company.domain.enums.audit.AuditOperation;
import com.sanigest.company.domain.enums.audit.AuditResult;
import com.sanigest.company.domain.enums.team.CompanyRole;
import com.sanigest.company.domain.enums.team.MembershipStatus;
import com.sanigest.company.domain.model.advisor.AdvisorPermission;
import com.sanigest.company.domain.model.audit.CompanyAuditEvent;
import com.sanigest.company.domain.model.team.Membership;
import com.sanigest.security.api.observability.RequestCorrelation;
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
            newStatus,
            null,
            RequestCorrelation.current(),
            null));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void advisorChanged(
      UUID companyId,
      UUID actorId,
      UUID advisorId,
      AuditOperation operation,
      AdvisorPermission permission) {
    repository.append(
        new CompanyAuditEvent(
            UUID.randomUUID(),
            companyId,
            actorId,
            advisorId,
            operation,
            clock.instant(),
            AuditResult.SUCCESS,
            null,
            null,
            null,
            null,
            permission,
            RequestCorrelation.current(),
            null));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void invitationChanged(
      UUID company, UUID actor, UUID invitationId, AuditOperation operation) {
    repository.append(
        new CompanyAuditEvent(
            UUID.randomUUID(),
            company,
            actor,
            null,
            operation,
            clock.instant(),
            AuditResult.SUCCESS,
            null,
            null,
            null,
            null,
            null,
            RequestCorrelation.current(),
            invitationId));
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
            null,
            null,
            RequestCorrelation.current(),
            null));
  }
}
