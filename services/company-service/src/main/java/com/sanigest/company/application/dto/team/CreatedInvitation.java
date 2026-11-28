package com.sanigest.company.application.dto.team;

import com.sanigest.company.domain.enums.team.CompanyRole;
import com.sanigest.company.domain.enums.team.InvitationStatus;
import java.time.Instant;
import java.util.UUID;

public record CreatedInvitation(
    UUID invitationId,
    CompanyRole role,
    InvitationStatus status,
    Instant expiresAt,
    String name,
    String email) {}
