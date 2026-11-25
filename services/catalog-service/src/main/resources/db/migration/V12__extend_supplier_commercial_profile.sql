ALTER TABLE catalog.suppliers
    ADD COLUMN address VARCHAR(500),
    ADD COLUMN supply_type VARCHAR(16) CHECK (supply_type IN ('PRODUCT', 'SERVICE', 'BOTH')),
    ADD COLUMN payment_terms VARCHAR(16) CHECK (payment_terms IN ('CASH', 'CREDIT', 'BOTH')),
    ADD COLUMN category_id UUID,
    ADD COLUMN credit_days INTEGER,
    ADD COLUMN observations VARCHAR(1000),
    ADD CONSTRAINT uq_suppliers_company_id UNIQUE (company_id, id),
    ADD CONSTRAINT fk_supplier_category FOREIGN KEY (company_id, category_id)
        REFERENCES catalog.supplier_categories (company_id, id),
    ADD CONSTRAINT ck_supplier_credit CHECK (credit_days IS NULL OR
        (credit_days >= 0 AND payment_terms IS NOT NULL AND payment_terms IN ('CREDIT', 'BOTH')));

CREATE INDEX ix_suppliers_company_category ON catalog.suppliers (company_id, category_id)
    WHERE category_id IS NOT NULL;
CREATE INDEX ix_suppliers_company_status_name ON catalog.suppliers (company_id, status, name, id);

-- Keep legacy identifiers byte-for-byte, including equivalent identifiers on separate rows.
-- New domestic documents are stored as digits and checked against this historical projection.
CREATE INDEX ix_suppliers_company_domestic_tax_id
    ON catalog.suppliers (company_id, (replace(replace(tax_id, ' ', ''), '-', '')))
    WHERE tax_id ~ '^[0-9][0-9 -]*[0-9]$';
