CREATE VIEW expense.audit_entries AS
SELECT id, company_id, actor_id, operation,
    CASE WHEN operation = 'PAYABLE_PAYMENT_REGISTERED' THEN 'PAYABLE_PAYMENT'
         ELSE 'EXPENSE' END AS resource_type,
    resource_id, occurred_at, 'SUCCESS'::text AS result,
    NULL::text AS previous_state, state, correlation_id
FROM expense.audit_log;
