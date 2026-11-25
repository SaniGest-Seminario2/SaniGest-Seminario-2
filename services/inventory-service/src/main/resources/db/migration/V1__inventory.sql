
CREATE SCHEMA inventory;

CREATE TABLE inventory.warehouses (
    company_id uuid NOT NULL,
    id uuid NOT NULL,
    code varchar(32) NOT NULL CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{0,31}$'),
    name varchar(120) NOT NULL CHECK (btrim(name) <> ''),
    location varchar(240),
    status varchar(8) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    revision bigint NOT NULL CHECK (revision >= 0),
    PRIMARY KEY (company_id, id),
    UNIQUE (company_id, code)
);

CREATE TABLE inventory.stock_positions (
    company_id uuid NOT NULL,
    catalog_item_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    tracking_enabled boolean NOT NULL,
    initialized boolean NOT NULL,
    minimum_stock numeric(15,3) NOT NULL CHECK (minimum_stock >= 0),
    maximum_stock numeric(15,3) CHECK (maximum_stock >= minimum_stock),
    location varchar(240),
    on_hand numeric(15,3) NOT NULL CHECK (on_hand >= 0),
    reserved numeric(15,3) NOT NULL CHECK (reserved >= 0 AND reserved <= on_hand),
    unit_code varchar(64) NOT NULL,
    divisible boolean NOT NULL,
    sku text,
    item_name text NOT NULL,
    catalog_verified_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    revision bigint NOT NULL CHECK (revision >= 0),
    movement_sequence bigint NOT NULL CHECK (movement_sequence >= 0),
    PRIMARY KEY (company_id, catalog_item_id, warehouse_id),
    FOREIGN KEY (company_id, warehouse_id) REFERENCES inventory.warehouses (company_id, id),
    CHECK (NOT tracking_enabled OR initialized),
    CHECK (tracking_enabled OR on_hand = 0),
    CHECK (divisible OR (on_hand = trunc(on_hand) AND reserved = trunc(reserved)
        AND minimum_stock = trunc(minimum_stock) AND (maximum_stock IS NULL OR maximum_stock = trunc(maximum_stock))))
);
CREATE INDEX stock_by_warehouse ON inventory.stock_positions (company_id, warehouse_id, catalog_item_id);

CREATE TABLE inventory.stock_movements (
    company_id uuid NOT NULL,
    id uuid NOT NULL,
    catalog_item_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    movement_type varchar(32) NOT NULL,
    direction varchar(3) NOT NULL CHECK (direction IN ('IN', 'OUT')),
    quantity numeric(15,3) NOT NULL CHECK (quantity > 0),
    unit_code varchar(64) NOT NULL,
    origin_type varchar(32) NOT NULL,
    origin_id uuid NOT NULL,
    effect_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    reason varchar(500) NOT NULL CHECK (btrim(reason) <> ''),
    occurred_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL,
    sequence bigint NOT NULL CHECK (sequence > 0),
    balance_after numeric(15,3) NOT NULL CHECK (balance_after >= 0),
    PRIMARY KEY (company_id, id),
    UNIQUE (company_id, effect_id),
    UNIQUE (company_id, catalog_item_id, warehouse_id, sequence),
    FOREIGN KEY (company_id, catalog_item_id, warehouse_id)
        REFERENCES inventory.stock_positions (company_id, catalog_item_id, warehouse_id),
    CHECK ((movement_type = 'INITIAL_STOCK' AND direction = 'IN' AND origin_type = 'STOCK_CONFIGURATION')
        OR (movement_type = 'MANUAL_ADJUSTMENT' AND origin_type = 'MANUAL_ADJUSTMENT'))
);
CREATE UNIQUE INDEX one_initial_stock ON inventory.stock_movements (company_id, catalog_item_id, warehouse_id)
    WHERE movement_type = 'INITIAL_STOCK';
CREATE INDEX movement_timeline ON inventory.stock_movements (company_id, occurred_at, sequence, id);
CREATE INDEX movement_by_warehouse ON inventory.stock_movements (company_id, warehouse_id, occurred_at);
CREATE INDEX movement_by_origin ON inventory.stock_movements (company_id, origin_type, origin_id);

CREATE FUNCTION inventory.reject_movement_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Physical movements are immutable' USING ERRCODE = '55000';
END;
$$;
CREATE TRIGGER stock_movements_append_only
    BEFORE UPDATE OR DELETE OR TRUNCATE ON inventory.stock_movements
    FOR EACH STATEMENT EXECUTE FUNCTION inventory.reject_movement_mutation();

CREATE TABLE inventory.idempotency_keys (
    company_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    operation varchar(32) NOT NULL CHECK (operation IN ('INITIALIZE_STOCK', 'ADJUST_STOCK')),
    key varchar(128) NOT NULL,
    fingerprint char(64) NOT NULL,
    result jsonb,
    PRIMARY KEY (company_id, actor_id, operation, key)
);
