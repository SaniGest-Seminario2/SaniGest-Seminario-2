package com.sanigest.identity.application;

public record RegisterUserCommand(String email, String password, String name) {}
