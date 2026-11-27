ALTER TABLE inventory.stock_movements
    DROP CONSTRAINT stock_movements_check,
    ADD CONSTRAINT stock_movements_origin_check CHECK (
        (movement_type = 'INITIAL_STOCK' AND direction = 'IN' AND origin_type = 'STOCK_CONFIGURATION')
        OR (movement_type = 'MANUAL_ADJUSTMENT' AND origin_type = 'MANUAL_ADJUSTMENT')
        OR (movement_type = 'PURCHASE_RECEIPT' AND direction = 'IN' AND origin_type = 'PURCHASE_RECEIPT')
    );

ALTER TABLE inventory.idempotency_keys
    DROP CONSTRAINT idempotency_keys_operation_check,
    ADD CONSTRAINT idempotency_keys_operation_check
        CHECK (operation IN ('INITIALIZE_STOCK', 'ADJUST_STOCK', 'PURCHASE_RECEIPT'));
