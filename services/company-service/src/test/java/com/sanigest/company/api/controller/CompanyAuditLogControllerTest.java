package com.sanigest.company.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sanigest.company.api.dto.CompanyAuditResponse;
import com.sanigest.company.application.account.AccountType;
import com.sanigest.company.application.dto.AuditPage;
import com.sanigest.company.application.usecase.audit.GetCompanyAuditLogUseCase;
import com.sanigest.company.domain.enums.AuditOperation;
import com.sanigest.company.domain.enums.AuditResult;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import com.sanigest.company.domain.model.CompanyAuditEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CompanyAuditLogControllerTest {
  @Test
  void mapsAuditEntriesWithoutInventingPriorValues() {
    var actor = UUID.randomUUID();
    var company = UUID.randomUUID();
    var user = UUID.randomUUID();
    var eventId = UUID.randomUUID();
    var occurredAt = Instant.parse("2026-11-22T00:00:00Z");
    var event =
        new CompanyAuditEvent(
            eventId,
            company,
            actor,
            user,
            AuditOperation.MEMBER_ACTIVATED,
            occurredAt,
            AuditResult.SUCCESS,
            null,
            CompanyRole.VIEWER,
            null,
            MembershipStatus.ACTIVE);
    var auditLog = mock(GetCompanyAuditLogUseCase.class);
    when(auditLog.get(actor, null, null, 2, 20))
        .thenReturn(new AuditPage(List.of(event), 2, 20, true));
    var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject(actor.toString()).build();

    var response =
        new CompanyAuditLogController(token -> AccountType.REGULAR, auditLog)
            .get(jwt, null, null, 2, 20);

    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(response.getBody())
        .isEqualTo(
            new CompanyAuditResponse(
                List.of(
                    new CompanyAuditResponse.Entry(
                        eventId,
                        company,
                        actor,
                        user,
                        "MEMBER_ACTIVATED",
                        occurredAt,
                        "SUCCESS",
                        null,
                        "VIEWER",
                        null,
                        "ACTIVE")),
                2,
                20,
                true));
  }
}
