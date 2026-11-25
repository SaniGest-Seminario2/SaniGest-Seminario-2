CREATE TABLE catalog.supplier_categories (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    code VARCHAR(32) NOT NULL CHECK (code ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    UNIQUE (company_id, id),
    UNIQUE (company_id, code)
);
