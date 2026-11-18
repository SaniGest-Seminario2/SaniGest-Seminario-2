CREATE TABLE company_memberships (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    user_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_company_memberships_role CHECK (role IN ('OWNER', 'ADMIN', 'OPERATOR', 'VIEWER')),
    CONSTRAINT ck_company_memberships_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT uq_company_memberships_company_user UNIQUE (company_id, user_id)
);

CREATE INDEX ix_company_memberships_user ON company_memberships (user_id);

CREATE TABLE company_permissions (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    permission VARCHAR(80) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_company_permissions_company_permission UNIQUE (company_id, permission),
    CONSTRAINT ck_company_permissions_name CHECK (TRIM(permission) <> '')
);

CREATE TABLE membership_permissions (
    membership_id UUID NOT NULL REFERENCES company_memberships (id),
    permission_id UUID NOT NULL REFERENCES company_permissions (id),
    PRIMARY KEY (membership_id, permission_id)
);
