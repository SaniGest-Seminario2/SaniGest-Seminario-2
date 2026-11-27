CREATE TABLE expense.payment_outbox (
    event_id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    source_id UUID NOT NULL,
    original_event_id UUID,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    available_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    attempts INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    UNIQUE (company_id, source_id),
    UNIQUE (company_id, event_id),
    CONSTRAINT payment_outbox_original_fk FOREIGN KEY (company_id, original_event_id)
        REFERENCES expense.payment_outbox (company_id, event_id)
);

CREATE INDEX payment_outbox_pending_idx
    ON expense.payment_outbox (available_at, created_at, event_id)
    WHERE published_at IS NULL;
