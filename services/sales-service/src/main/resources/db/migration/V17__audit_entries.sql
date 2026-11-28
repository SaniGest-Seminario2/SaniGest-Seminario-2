CREATE VIEW sales.audit_entries AS
SELECT id, company_id, actor_id, operation,
    CASE operation
      WHEN 'INVOICE_PAYMENT_REGISTERED' THEN 'INVOICE_PAYMENT'
      WHEN 'INVOICE_ISSUED' THEN 'INVOICE'
      WHEN 'INVOICE_CANCELLED' THEN 'INVOICE'
      ELSE 'SALE'
    END AS resource_type,
    resource_id, occurred_at, 'SUCCESS'::text AS result,
    NULL::text AS previous_state, state, correlation_id
FROM sales.audit_log;
