ALTER TABLE sales.internal_invoices
    DROP CONSTRAINT internal_invoices_status_check,
    ALTER COLUMN status TYPE VARCHAR(9),
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancelled_by UUID,
    ADD COLUMN cancel_reason VARCHAR(500),
    ADD CONSTRAINT invoice_lifecycle_check CHECK (
        (status = 'ISSUED' AND cancelled_at IS NULL AND cancelled_by IS NULL AND cancel_reason IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL
            AND cancel_reason IS NOT NULL AND cancel_reason ~ '[^[:space:]]')
    );

-- A cancelled document is terminal and retains its one-to-one source sale.
DROP INDEX sales.invoice_one_issued_per_sale;
CREATE UNIQUE INDEX invoice_one_issued_per_sale ON sales.internal_invoices (company_id, sale_id);

CREATE INDEX invoice_company_date_idx
    ON sales.internal_invoices (company_id, sale_date DESC, id DESC);
