package com.sanigest.company.application.dto;

import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.InvitationStatus;
import java.time.Instant;
import java.util.UUID;

public record CreatedInvitation(
    UUID invitationId, CompanyRole role, InvitationStatus status, Instant expiresAt) {}
