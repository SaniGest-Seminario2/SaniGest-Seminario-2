package com.sanigest.identity.application.dto.account;

import java.util.UUID;

public record AuthenticatedUserProfile(UUID id, String name, String email, String accountType) {}
