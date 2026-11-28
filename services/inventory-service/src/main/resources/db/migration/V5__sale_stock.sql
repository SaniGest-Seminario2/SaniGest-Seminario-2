ALTER TABLE inventory.stock_movements
    DROP CONSTRAINT stock_movements_origin_check,
    ADD CONSTRAINT stock_movements_origin_check CHECK (
        (movement_type = 'INITIAL_STOCK' AND direction = 'IN' AND origin_type = 'STOCK_CONFIGURATION')
        OR (movement_type = 'MANUAL_ADJUSTMENT' AND origin_type = 'MANUAL_ADJUSTMENT')
        OR (movement_type = 'PURCHASE_RECEIPT' AND direction = 'IN' AND origin_type = 'PURCHASE_RECEIPT')
        OR (movement_type = 'SALE_ISSUE' AND direction = 'OUT' AND origin_type = 'SALE')
        OR (movement_type = 'SALE_REVERSAL' AND direction = 'IN' AND origin_type = 'SALE')
    );

CREATE TABLE inventory.sale_stock_effects (
    company_id uuid NOT NULL,
    sale_id uuid NOT NULL,
    request_hash text,
    reversed boolean NOT NULL DEFAULT false,
    PRIMARY KEY (company_id, sale_id),
    CHECK (reversed OR request_hash IS NOT NULL)
);
