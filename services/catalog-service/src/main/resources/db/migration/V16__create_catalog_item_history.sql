CREATE TABLE catalog.catalog_item_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id UUID NOT NULL,
    catalog_item_id UUID NOT NULL,
    actor_id UUID,
    operation VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    previous_values JSONB,
    new_values JSONB,
    FOREIGN KEY (company_id, catalog_item_id) REFERENCES catalog.catalog_items (company_id, id)
);

CREATE INDEX ix_catalog_item_history_page
    ON catalog.catalog_item_history (company_id, catalog_item_id, id DESC);

CREATE FUNCTION catalog.catalog_item_audit_values(item catalog.catalog_items)
RETURNS JSONB LANGUAGE sql IMMUTABLE AS $$
    SELECT jsonb_build_object(
        'name', item.name, 'type', item.type, 'description', item.description,
        'price', item.price::text, 'estimatedCost', item.estimated_cost::text,
        'sku', item.sku, 'barcode', item.barcode, 'categoryId', item.category_id,
        'unitCode', item.unit_code, 'taxClassificationCode', item.tax_classification_code,
        'availableForSale', item.available_for_sale,
        'availableForPurchase', item.available_for_purchase, 'active', item.active);
$$;

CREATE FUNCTION catalog.audit_catalog_item() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    before_values JSONB;
    after_values JSONB := catalog.catalog_item_audit_values(NEW);
BEGIN
    IF TG_OP = 'UPDATE' THEN
        before_values := catalog.catalog_item_audit_values(OLD);
        IF before_values = after_values THEN
            RETURN NEW;
        END IF;
    END IF;

    INSERT INTO catalog.catalog_item_history
        (company_id, catalog_item_id, actor_id, operation, previous_values, new_values)
    VALUES (NEW.company_id, NEW.id, NULLIF(current_setting('sanigest.catalog_actor', true), '')::uuid,
        CASE WHEN TG_OP = 'INSERT' THEN 'CREATE'
             WHEN OLD.active IS DISTINCT FROM NEW.active THEN 'CHANGE_STATUS'
             ELSE 'UPDATE' END, before_values, after_values);
    RETURN NEW;
END;
$$;

CREATE TRIGGER audit_catalog_item AFTER INSERT OR UPDATE ON catalog.catalog_items
    FOR EACH ROW EXECUTE FUNCTION catalog.audit_catalog_item();

CREATE FUNCTION catalog.audit_primary_supplier() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    previous_supplier UUID;
    next_supplier UUID;
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.is_primary THEN
        previous_supplier := OLD.supplier_id;
    END IF;
    IF NEW.is_primary THEN
        next_supplier := NEW.supplier_id;
    END IF;
    IF previous_supplier IS DISTINCT FROM next_supplier THEN
        INSERT INTO catalog.catalog_item_history
            (company_id, catalog_item_id, actor_id, operation, previous_values, new_values)
        VALUES (NEW.company_id, NEW.catalog_item_id,
            NULLIF(current_setting('sanigest.catalog_actor', true), '')::uuid, 'PRIMARY_SUPPLIER',
            jsonb_build_object('primarySupplierId', previous_supplier),
            jsonb_build_object('primarySupplierId', next_supplier));
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER audit_primary_supplier AFTER INSERT OR UPDATE ON catalog.supplier_items
    FOR EACH ROW EXECUTE FUNCTION catalog.audit_primary_supplier();
