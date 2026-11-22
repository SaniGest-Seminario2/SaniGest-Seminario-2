CREATE TABLE company_advisor_access (
    company_id UUID NOT NULL REFERENCES companies (id),
    advisor_id UUID NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    PRIMARY KEY (company_id, advisor_id)
);

CREATE TABLE company_advisor_permissions (
    company_id UUID NOT NULL,
    advisor_id UUID NOT NULL,
    module VARCHAR(16) NOT NULL CHECK (module IN ('CATALOG', 'SALES', 'EXPENSE', 'FINANCE')),
    scope VARCHAR(8) NOT NULL CHECK (scope IN ('READ', 'WRITE')),
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE CHECK (revoked_at >= granted_at),
    PRIMARY KEY (company_id, advisor_id, module, scope),
    FOREIGN KEY (company_id, advisor_id) REFERENCES company_advisor_access (company_id, advisor_id)
);

ALTER TABLE company_audit_log ADD COLUMN advisor_module VARCHAR(16)
    CHECK (advisor_module IN ('CATALOG', 'SALES', 'EXPENSE', 'FINANCE'));
ALTER TABLE company_audit_log ADD COLUMN advisor_scope VARCHAR(8) CHECK (advisor_scope IN ('READ', 'WRITE'));
