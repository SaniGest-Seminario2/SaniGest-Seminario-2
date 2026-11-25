CREATE FUNCTION catalog.client_tax_id_key(document TEXT)
RETURNS TEXT LANGUAGE SQL IMMUTABLE STRICT PARALLEL SAFE
RETURN CASE
    WHEN document ~ '^[0-9][0-9 -]*[0-9]$' AND length(translate(document, ' -', '')) IN (9, 11)
        THEN translate(document, ' -', '')
    ELSE document
END;

ALTER TABLE catalog.clients
    ADD COLUMN client_type VARCHAR(16) CHECK (client_type IN ('NATURAL', 'LEGAL')),
    ADD COLUMN alternate_phone VARCHAR(32),
    ADD COLUMN country_code VARCHAR(2),
    ADD COLUMN province_code VARCHAR(8),
    ADD COLUMN municipality_code VARCHAR(8),
    ADD COLUMN preferred_receipt_type VARCHAR(32),
    ADD COLUMN payment_terms VARCHAR(16) CHECK (payment_terms IN ('CASH', 'CREDIT', 'BOTH')),
    ADD COLUMN category_id UUID,
    ADD COLUMN credit_limit NUMERIC(19,2) CHECK (credit_limit >= 0),
    ADD COLUMN observations VARCHAR(1000),
    ADD COLUMN enforce_tax_key BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN tax_id_key TEXT GENERATED ALWAYS AS (catalog.client_tax_id_key(tax_id)) STORED,
    ADD CONSTRAINT fk_client_profile_country FOREIGN KEY (country_code)
        REFERENCES catalog.client_countries,
    ADD CONSTRAINT fk_client_profile_province FOREIGN KEY (country_code, province_code)
        REFERENCES catalog.client_provinces,
    ADD CONSTRAINT fk_client_profile_municipality FOREIGN KEY (country_code, province_code, municipality_code)
        REFERENCES catalog.client_municipalities,
    ADD CONSTRAINT fk_client_profile_receipt FOREIGN KEY (preferred_receipt_type)
        REFERENCES catalog.client_receipt_types,
    ADD CONSTRAINT fk_client_profile_category FOREIGN KEY (company_id, category_id)
        REFERENCES catalog.client_categories (company_id, id),
    ADD CONSTRAINT ck_clients_complete_profile CHECK (
        (client_type IS NULL AND country_code IS NULL AND payment_terms IS NULL AND category_id IS NULL
            AND alternate_phone IS NULL AND province_code IS NULL AND municipality_code IS NULL
            AND preferred_receipt_type IS NULL AND credit_limit IS NULL AND observations IS NULL)
        OR (client_type IS NOT NULL AND country_code IS NOT NULL
            AND payment_terms IS NOT NULL AND category_id IS NOT NULL
            AND (municipality_code IS NULL OR province_code IS NOT NULL))
    );

-- Every historical spelling remains unchanged. The lookup index retains all colliding keys
-- so new assignments can reject ambiguity; competing new assignments use the unique index.
ALTER TABLE catalog.clients ALTER COLUMN enforce_tax_key SET DEFAULT true;
CREATE INDEX ix_clients_company_tax_key ON catalog.clients (company_id, tax_id_key)
    WHERE tax_id_key IS NOT NULL;
CREATE UNIQUE INDEX uq_clients_company_tax_key ON catalog.clients (company_id, tax_id_key)
    WHERE enforce_tax_key AND tax_id_key IS NOT NULL;
