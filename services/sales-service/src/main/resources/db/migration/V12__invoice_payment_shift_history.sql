CREATE INDEX invoice_payments_shift_history_idx
    ON sales.invoice_payments (company_id, operator_id, shift_id, registered_at DESC, id DESC)
    WHERE registered_at IS NOT NULL;
