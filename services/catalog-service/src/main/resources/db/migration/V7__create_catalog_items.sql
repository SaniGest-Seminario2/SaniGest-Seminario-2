CREATE TABLE catalog.catalog_items (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(16) NOT NULL,
    price NUMERIC(14, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'DOP',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_catalog_items_name CHECK (name ~ '[^[:space:]]'),
    CONSTRAINT ck_catalog_items_type CHECK (type IN ('PRODUCT', 'SERVICE')),
    CONSTRAINT ck_catalog_items_price CHECK (price >= 0 AND price <= 999999999999.99),
    CONSTRAINT ck_catalog_items_currency CHECK (currency = 'DOP')
);
