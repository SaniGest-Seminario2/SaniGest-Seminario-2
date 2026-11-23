ALTER TABLE catalog.suppliers
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;

CREATE INDEX ix_suppliers_company_name_id
    ON catalog.suppliers (company_id, name, id);
