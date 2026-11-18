ALTER TABLE companies ADD COLUMN business_name VARCHAR(160) NOT NULL DEFAULT '';
ALTER TABLE companies ADD COLUMN email VARCHAR(320) NOT NULL DEFAULT '';
ALTER TABLE companies ADD COLUMN phone VARCHAR(32) NOT NULL DEFAULT '';
ALTER TABLE companies ADD COLUMN location VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE companies ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'DOP';
ALTER TABLE companies ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE companies ADD CONSTRAINT ck_companies_business_name_not_blank CHECK (TRIM(business_name) <> '');
ALTER TABLE companies ADD CONSTRAINT ck_companies_email_not_blank CHECK (TRIM(email) <> '');
ALTER TABLE companies ADD CONSTRAINT ck_companies_currency CHECK (currency = 'DOP');
ALTER TABLE companies ADD CONSTRAINT ck_companies_status CHECK (status IN ('ACTIVE', 'INACTIVE'));
ALTER TABLE company_memberships ADD CONSTRAINT uq_company_memberships_user UNIQUE (user_id);

CREATE TABLE company_registration_idempotency (
    user_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    company_id UUID NOT NULL REFERENCES companies (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, idempotency_key)
);
