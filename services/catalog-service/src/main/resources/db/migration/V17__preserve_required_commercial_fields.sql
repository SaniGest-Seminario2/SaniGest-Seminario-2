-- Existing rows may have unknown commercial values. Once a value is known, a
-- later edit must not turn it back into an unknown historical value.
CREATE FUNCTION catalog.preserve_item_commercial_fields() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF (OLD.unit_code IS NOT NULL AND NEW.unit_code IS NULL)
        OR (OLD.tax_classification_code IS NOT NULL AND NEW.tax_classification_code IS NULL)
        OR (OLD.available_for_purchase IS NOT NULL AND NEW.available_for_purchase IS NULL) THEN
        RAISE check_violation USING MESSAGE = 'Required catalog item field cannot be cleared';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER preserve_item_commercial_fields
BEFORE UPDATE OF unit_code, tax_classification_code, available_for_purchase
ON catalog.catalog_items
FOR EACH ROW EXECUTE FUNCTION catalog.preserve_item_commercial_fields();

CREATE FUNCTION catalog.preserve_supplier_payment_terms() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.payment_terms IS NOT NULL AND NEW.payment_terms IS NULL THEN
        RAISE check_violation USING MESSAGE = 'Supplier payment terms cannot be cleared';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER preserve_supplier_payment_terms
BEFORE UPDATE OF payment_terms ON catalog.suppliers
FOR EACH ROW EXECUTE FUNCTION catalog.preserve_supplier_payment_terms();
