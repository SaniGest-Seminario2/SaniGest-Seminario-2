package com.sanigest.company.api.dto.team;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
    UUID invitationId, String role, String status, Instant expiresAt, String name, String email) {}
