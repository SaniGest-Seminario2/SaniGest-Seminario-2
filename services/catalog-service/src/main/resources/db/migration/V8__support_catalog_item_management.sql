ALTER TABLE catalog.catalog_items
    ADD COLUMN description VARCHAR(1000),
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;

-- Supports tenant-scoped listing in the default sort order.
CREATE INDEX ix_catalog_items_company_name_id ON catalog.catalog_items (company_id, name, id);
