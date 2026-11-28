package com.sanigest.identity.application.dto.registration;

public record RegisterUserCommand(String email, String password, String name) {}
