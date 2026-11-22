CREATE TABLE company_audit_log (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    actor_id UUID NOT NULL,
    target_user_id UUID,
    operation VARCHAR(24) NOT NULL CHECK (
        operation IN ('ROLE_CHANGED', 'MEMBER_ACTIVATED', 'MEMBER_REVOKED')
    ),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    result VARCHAR(16) NOT NULL CHECK (result IN ('SUCCESS', 'REJECTED')),
    previous_role VARCHAR(16) CHECK (previous_role IN ('OWNER', 'ADMIN', 'OPERATOR', 'VIEWER')),
    new_role VARCHAR(16) CHECK (new_role IN ('OWNER', 'ADMIN', 'OPERATOR', 'VIEWER')),
    previous_status VARCHAR(16) CHECK (previous_status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    new_status VARCHAR(16) CHECK (new_status IN ('ACTIVE', 'SUSPENDED', 'REVOKED'))
);

CREATE INDEX ix_company_audit_log_page ON company_audit_log (company_id, occurred_at DESC, id DESC);
