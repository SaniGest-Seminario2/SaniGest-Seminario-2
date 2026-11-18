package com.sanigest.identity.api;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {}
