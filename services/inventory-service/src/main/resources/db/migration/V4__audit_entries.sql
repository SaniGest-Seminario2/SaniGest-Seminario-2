CREATE VIEW inventory.audit_entries AS
SELECT id, company_id, actor_id, movement_type AS operation,
    'STOCK_MOVEMENT'::text AS resource_type, id AS resource_id,
    occurred_at, 'SUCCESS'::text AS result, NULL::text AS previous_state,
    direction AS state, NULL::text AS correlation_id
FROM inventory.stock_movements;
