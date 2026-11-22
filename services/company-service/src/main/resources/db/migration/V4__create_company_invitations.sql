CREATE TABLE company_invitations (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    email VARCHAR(320) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('OPERATOR', 'VIEWER')),
    token_hash CHAR(64) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    pending_slot INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (email <> '' AND email = LOWER(TRIM(email))),
    CHECK (expires_at > created_at),
    CHECK (
        (status = 'PENDING' AND pending_slot IS NOT NULL AND pending_slot = 1)
        OR (status IN ('ACCEPTED', 'EXPIRED', 'REVOKED') AND pending_slot IS NULL)
    ),
    CONSTRAINT uq_company_invitations_pending UNIQUE (company_id, email, pending_slot)
);
