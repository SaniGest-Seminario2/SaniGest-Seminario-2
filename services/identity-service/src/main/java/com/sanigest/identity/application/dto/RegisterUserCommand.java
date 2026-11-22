package com.sanigest.identity.application.dto;

public record RegisterUserCommand(String email, String password, String name) {}
