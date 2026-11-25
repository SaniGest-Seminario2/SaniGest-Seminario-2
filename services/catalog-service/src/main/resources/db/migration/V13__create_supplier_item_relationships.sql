ALTER TABLE catalog.catalog_items ADD CONSTRAINT uq_catalog_items_company_id UNIQUE (company_id, id);

CREATE TABLE catalog.supplier_items (
    company_id UUID NOT NULL,
    supplier_id UUID NOT NULL,
    catalog_item_id UUID NOT NULL,
    offered_cost NUMERIC(19,4) CHECK (offered_cost >= 0),
    supplier_reference VARCHAR(160),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    valid_from TIMESTAMPTZ NOT NULL DEFAULT statement_timestamp(),
    valid_until TIMESTAMPTZ,
    PRIMARY KEY (company_id, supplier_id, catalog_item_id),
    FOREIGN KEY (company_id, supplier_id) REFERENCES catalog.suppliers (company_id, id),
    FOREIGN KEY (company_id, catalog_item_id) REFERENCES catalog.catalog_items (company_id, id),
    CHECK (valid_until IS NULL OR (valid_until >= valid_from AND NOT is_primary))
);

CREATE INDEX ix_supplier_items_item ON catalog.supplier_items (company_id, catalog_item_id, supplier_id);
CREATE UNIQUE INDEX uq_supplier_items_primary
    ON catalog.supplier_items (company_id, catalog_item_id) WHERE is_primary;

-- Status is changed with the existing optimistic UPDATE. Retire links in that same statement
-- transaction, including writes made outside the HTTP status endpoints.
CREATE FUNCTION catalog.retire_supplier_items() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM i.id FROM catalog.catalog_items i
    JOIN catalog.supplier_items si ON si.company_id = i.company_id AND si.catalog_item_id = i.id
    WHERE si.company_id = NEW.company_id AND si.supplier_id = NEW.id AND si.valid_until IS NULL
    ORDER BY i.id FOR NO KEY UPDATE OF i;

    UPDATE catalog.supplier_items SET valid_until = clock_timestamp(), is_primary = FALSE
    WHERE company_id = NEW.company_id AND supplier_id = NEW.id AND valid_until IS NULL;
    RETURN NEW;
END;
$$;

CREATE FUNCTION catalog.retire_catalog_item_suppliers() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE catalog.supplier_items SET valid_until = clock_timestamp(), is_primary = FALSE
    WHERE company_id = NEW.company_id AND catalog_item_id = NEW.id AND valid_until IS NULL;
    RETURN NEW;
END;
$$;

CREATE TRIGGER retire_inactive_supplier_items AFTER UPDATE OF status ON catalog.suppliers
    FOR EACH ROW WHEN (OLD.status = 'ACTIVE' AND NEW.status = 'INACTIVE')
    EXECUTE FUNCTION catalog.retire_supplier_items();
CREATE TRIGGER retire_inactive_catalog_item_suppliers AFTER UPDATE OF active ON catalog.catalog_items
    FOR EACH ROW WHEN (OLD.active AND NOT NEW.active)
    EXECUTE FUNCTION catalog.retire_catalog_item_suppliers();
