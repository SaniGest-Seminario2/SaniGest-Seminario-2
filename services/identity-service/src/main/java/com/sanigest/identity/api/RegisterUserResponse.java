package com.sanigest.identity.api;

import java.util.UUID;

public record RegisterUserResponse(UUID userId, String email) {}
