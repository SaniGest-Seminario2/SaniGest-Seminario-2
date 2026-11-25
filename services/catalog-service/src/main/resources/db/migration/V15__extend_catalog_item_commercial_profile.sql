ALTER TABLE catalog.catalog_items
    DROP CONSTRAINT ck_catalog_items_price,
    ALTER COLUMN price TYPE NUMERIC(19,4),
    ADD CONSTRAINT ck_catalog_items_price CHECK (price >= 0 AND price < 1000000000000000),
    ADD COLUMN sku VARCHAR(64) CHECK (sku <> '' AND sku = upper(btrim(sku))),
    ADD COLUMN barcode VARCHAR(64) CHECK (barcode <> '' AND barcode = btrim(barcode)),
    ADD COLUMN category_id UUID,
    ADD COLUMN unit_code VARCHAR(32) REFERENCES catalog.units_of_measure,
    ADD COLUMN estimated_cost NUMERIC(19,4)
        CHECK (estimated_cost >= 0 AND estimated_cost < 1000000000000000),
    ADD COLUMN tax_classification_code VARCHAR(32) REFERENCES catalog.tax_classifications,
    ADD COLUMN available_for_sale BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN available_for_purchase BOOLEAN,
    ADD CONSTRAINT uq_catalog_items_sku UNIQUE (company_id, sku),
    ADD CONSTRAINT uq_catalog_items_barcode UNIQUE (company_id, barcode),
    ADD FOREIGN KEY (company_id, category_id) REFERENCES catalog.catalog_item_categories (company_id, id);

CREATE INDEX ix_catalog_items_category ON catalog.catalog_items (company_id, category_id);

CREATE FUNCTION catalog.clear_ineligible_primary() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE catalog.supplier_items SET is_primary = FALSE
    WHERE company_id = NEW.company_id AND catalog_item_id = NEW.id AND is_primary;
    RETURN NEW;
END;
$$;

CREATE TRIGGER clear_ineligible_primary AFTER UPDATE OF available_for_purchase ON catalog.catalog_items
    FOR EACH ROW WHEN (OLD.available_for_purchase IS DISTINCT FROM NEW.available_for_purchase
        AND NEW.available_for_purchase IS FALSE)
    EXECUTE FUNCTION catalog.clear_ineligible_primary();
