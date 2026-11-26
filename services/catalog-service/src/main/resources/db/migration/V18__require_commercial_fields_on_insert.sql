-- Prospective enforcement. Identical historical rows remain readable and editable.
CREATE FUNCTION catalog.require_new_commercial_fields() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_TABLE_NAME = 'clients' THEN
        IF NEW.client_type IS NULL OR NEW.country_code IS NULL
            OR NEW.payment_terms IS NULL OR NEW.category_id IS NULL THEN
            RAISE check_violation USING MESSAGE = 'New client requires a complete commercial profile';
        END IF;
    ELSIF TG_TABLE_NAME = 'suppliers' THEN
        IF NEW.payment_terms IS NULL THEN
            RAISE check_violation USING MESSAGE = 'New supplier requires payment terms';
        END IF;
    ELSIF NEW.unit_code IS NULL OR NEW.tax_classification_code IS NULL
        OR NEW.available_for_purchase IS NULL THEN
        RAISE check_violation USING MESSAGE = 'New catalog item requires unit, tax classification and purchase availability';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER require_new_client_profile BEFORE INSERT ON catalog.clients
FOR EACH ROW EXECUTE FUNCTION catalog.require_new_commercial_fields();

CREATE TRIGGER require_new_supplier_profile BEFORE INSERT ON catalog.suppliers
FOR EACH ROW EXECUTE FUNCTION catalog.require_new_commercial_fields();

CREATE TRIGGER require_new_catalog_item_profile BEFORE INSERT ON catalog.catalog_items
FOR EACH ROW EXECUTE FUNCTION catalog.require_new_commercial_fields();
