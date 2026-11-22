package com.sanigest.identity.application.dto;

import java.util.UUID;

public record AuthenticatedUserProfile(UUID id, String name, String email, String accountType) {}
