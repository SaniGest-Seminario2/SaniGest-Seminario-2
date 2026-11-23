-- V1-V3 allowed historical cancellations without metadata. Preserve those rows
-- and enforce the full invariant for all new or updated rows.
ALTER TABLE sales.sales
    ADD COLUMN cancelled_by UUID,
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancel_reason VARCHAR(500),
    ADD CONSTRAINT sales_cancellation_metadata CHECK (
        CASE status
            WHEN 'REGISTERED' THEN cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL
            WHEN 'CANCELLED' THEN cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL
                AND cancel_reason IS NOT NULL AND cancel_reason ~ '[^[:space:]]'
            ELSE TRUE
        END
    ) NOT VALID;
