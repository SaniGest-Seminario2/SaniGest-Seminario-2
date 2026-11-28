package com.sanigest.identity.application.dto.session;

public record IssuedRefreshToken(String value, long expiresIn) {}
