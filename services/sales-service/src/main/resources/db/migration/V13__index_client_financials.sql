CREATE INDEX receivables_client_pending_idx ON sales.receivables (company_id, client_id)
    WHERE status IN ('OPEN', 'PARTIALLY_PAID');

CREATE INDEX invoices_client_history_idx
    ON sales.internal_invoices (company_id, client_id, sale_date DESC, id DESC);

CREATE INDEX invoice_payments_invoice_history_idx
    ON sales.invoice_payments (company_id, invoice_id, registered_at DESC, id DESC)
    WHERE registered_at IS NOT NULL;
