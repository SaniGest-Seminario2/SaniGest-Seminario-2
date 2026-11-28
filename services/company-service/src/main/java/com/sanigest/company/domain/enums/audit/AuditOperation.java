package com.sanigest.company.domain.enums.audit;

public enum AuditOperation {
  ROLE_CHANGED,
  MEMBER_ACTIVATED,
  MEMBER_REVOKED,
  MEMBER_SUSPENDED,
  INVITATION_CREATED,
  INVITATION_REPLACED,
  INVITATION_REVOKED,
  ADVISOR_GRANTED,
  ADVISOR_REVOKED;

  public String resourceType() {
    return switch (this) {
      case INVITATION_CREATED, INVITATION_REPLACED, INVITATION_REVOKED -> "INVITATION";
      case ADVISOR_GRANTED, ADVISOR_REVOKED -> "ADVISOR_ACCESS";
      default -> "MEMBERSHIP";
    };
  }
}
