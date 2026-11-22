package com.sanigest.company.application.dto;

public record CreateCompanyCommand(
    String companyName,
    String businessName,
    String rnc,
    String email,
    String phone,
    String location,
    String ownerId,
    String userId) {}
