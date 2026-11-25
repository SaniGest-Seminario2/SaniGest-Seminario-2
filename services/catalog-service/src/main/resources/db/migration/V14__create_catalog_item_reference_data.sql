CREATE TABLE catalog.catalog_item_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    code VARCHAR(32) NOT NULL CHECK (code ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    name VARCHAR(100) NOT NULL CHECK (name ~ '[^[:space:]]'),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (company_id, code),
    UNIQUE (company_id, id)
);

CREATE TABLE catalog.units_of_measure (
    code VARCHAR(32) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    divisible BOOLEAN NOT NULL
);

INSERT INTO catalog.units_of_measure (code, name, divisible) VALUES
    ('UNIT', 'Unidad', FALSE), ('BOX', 'Caja', FALSE), ('PACK', 'Paquete', FALSE),
    ('LB', 'Libra', TRUE), ('KG', 'Kilogramo', TRUE), ('L', 'Litro', TRUE), ('M', 'Metro', TRUE);

CREATE TABLE catalog.tax_classifications (
    code VARCHAR(32) PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE catalog.tax_rate_versions (
    classification_code VARCHAR(32) NOT NULL REFERENCES catalog.tax_classifications,
    effective_from DATE NOT NULL,
    effective_until DATE,
    rate NUMERIC(7,6) NOT NULL CHECK (rate BETWEEN 0 AND 1),
    PRIMARY KEY (classification_code, effective_from),
    CHECK (effective_until IS NULL OR effective_until > effective_from),
    EXCLUDE USING gist (classification_code WITH =,
        daterange(effective_from, effective_until, '[)') WITH &&)
);

INSERT INTO catalog.tax_classifications VALUES
    ('ITBIS_18', 'ITBIS 18%'), ('ITBIS_16', 'ITBIS 16%'), ('EXEMPT', 'Exento');

INSERT INTO catalog.tax_rate_versions (classification_code, effective_from, rate) VALUES
    ('ITBIS_18', '2026-01-01', 0.180000), ('ITBIS_16', '2026-01-01', 0.160000),
    ('EXEMPT', '2026-01-01', 0.000000);

-- A currently or previously effective version keeps its code, start and rate.
-- A current open window can close prospectively when a new version is scheduled.
CREATE FUNCTION catalog.protect_effective_tax_rate() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    business_date DATE := (statement_timestamp() AT TIME ZONE 'America/Santo_Domingo')::date;
BEGIN
    IF OLD.effective_from <= business_date THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'An effective tax version cannot be deleted' USING ERRCODE = '23514';
        END IF;
        IF (OLD.classification_code, OLD.effective_from, OLD.rate)
                IS DISTINCT FROM (NEW.classification_code, NEW.effective_from, NEW.rate)
            OR (OLD.effective_until IS DISTINCT FROM NEW.effective_until
                AND (OLD.effective_until <= business_date OR NEW.effective_until <= business_date)) THEN
            RAISE EXCEPTION 'An effective tax version cannot be changed retrospectively'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER protect_effective_tax_rate BEFORE UPDATE OR DELETE ON catalog.tax_rate_versions
    FOR EACH ROW EXECUTE FUNCTION catalog.protect_effective_tax_rate();
