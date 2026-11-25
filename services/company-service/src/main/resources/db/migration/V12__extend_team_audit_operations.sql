ALTER TABLE company_audit_log DROP CONSTRAINT company_audit_log_operation_check;
ALTER TABLE company_audit_log ADD CONSTRAINT company_audit_log_operation_check CHECK (
    operation IN ('ROLE_CHANGED', 'MEMBER_ACTIVATED', 'MEMBER_REVOKED', 'MEMBER_SUSPENDED',
        'ADVISOR_GRANTED', 'ADVISOR_REVOKED',
        'INVITATION_CREATED', 'INVITATION_REPLACED', 'INVITATION_REVOKED')
);
