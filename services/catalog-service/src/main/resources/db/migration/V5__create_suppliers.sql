CREATE TABLE catalog.suppliers (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    tax_id VARCHAR(64),
    contact VARCHAR(160) NOT NULL,
    phone VARCHAR(32),
    email VARCHAR(320),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT ck_suppliers_name CHECK (name ~ '[^[:space:]]'),
    CONSTRAINT ck_suppliers_contact CHECK (contact ~ '[^[:space:]]'),
    CONSTRAINT ck_suppliers_tax_id CHECK (tax_id IS NULL OR
        (tax_id <> '' AND tax_id !~ '^[[:space:]]|[[:space:]]$')),
    CONSTRAINT ck_suppliers_phone CHECK (phone IS NULL OR
        (phone ~ '^\+?[0-9() .-]{7,32}$' AND length(regexp_replace(phone, '[^0-9]', '', 'g')) >= 7)),
    CONSTRAINT ck_suppliers_email CHECK (email IS NULL OR
        email ~ '^[^[:space:]@]+@[^[:space:]@.]+(\.[^[:space:]@.]+)+$'),
    CONSTRAINT ck_suppliers_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uq_suppliers_company_tax_id
    ON catalog.suppliers (company_id, tax_id)
    WHERE tax_id IS NOT NULL;
