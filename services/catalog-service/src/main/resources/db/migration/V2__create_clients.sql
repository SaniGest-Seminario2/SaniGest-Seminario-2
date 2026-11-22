CREATE TABLE catalog.clients (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    tax_id VARCHAR(64),
    email VARCHAR(320),
    phone VARCHAR(32),
    address VARCHAR(255),
    status VARCHAR(16) NOT NULL,
    CONSTRAINT ck_clients_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_clients_tax_id CHECK (tax_id IS NULL OR (tax_id <> '' AND tax_id = btrim(tax_id, E' \t\n\r\f\013'))),
    CONSTRAINT ck_clients_status CHECK (status = 'ACTIVE')
);

CREATE UNIQUE INDEX uq_clients_company_tax_id
    ON catalog.clients (company_id, tax_id)
    WHERE tax_id IS NOT NULL;
