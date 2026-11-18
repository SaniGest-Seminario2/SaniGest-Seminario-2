package com.sanigest.identity.application;

public record AccessToken(String value, String type, long expiresIn) {}
