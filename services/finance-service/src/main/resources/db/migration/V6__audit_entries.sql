CREATE INDEX cash_movements_audit_timeline
    ON finance.cash_movements (company_id, occurred_at DESC, event_id DESC);

CREATE VIEW finance.audit_entries AS
SELECT event_id AS id, company_id, actor_id, event_type AS operation,
    'CASH_MOVEMENT'::text AS resource_type, event_id AS resource_id,
    occurred_at, 'SUCCESS'::text AS result, NULL::text AS previous_state,
    direction AS state, trace_id AS correlation_id
FROM finance.cash_movements;
