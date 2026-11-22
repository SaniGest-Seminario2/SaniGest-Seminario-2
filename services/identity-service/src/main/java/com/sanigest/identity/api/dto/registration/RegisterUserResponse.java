package com.sanigest.identity.api.dto.registration;

import java.util.UUID;

public record RegisterUserResponse(UUID userId, String email) {}
