CREATE TABLE sales.sale_idempotency_keys (
    company_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    operation VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL CHECK (idempotency_key ~ '^[A-Za-z0-9._~-]{1,128}$'),
    request_hash VARCHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    fingerprint_version INTEGER NOT NULL CHECK (fingerprint_version > 0),
    claim_token UUID,
    sale_id UUID,
    response_body TEXT,
    response_status INTEGER,
    response_version INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (company_id, actor_id, operation, idempotency_key),
    CONSTRAINT sale_idempotency_sale_fk FOREIGN KEY (sale_id) REFERENCES sales.sales (id),
    CONSTRAINT sale_idempotency_state CHECK (
        (claim_token IS NOT NULL AND sale_id IS NULL AND response_body IS NULL
            AND response_status IS NULL AND response_version IS NULL AND completed_at IS NULL)
        OR (claim_token IS NULL AND sale_id IS NOT NULL AND response_body IS NOT NULL
            AND response_status IS NOT NULL AND response_status = 201
            AND response_version IS NOT NULL AND response_version > 0 AND completed_at IS NOT NULL)
    ),
    CONSTRAINT sale_idempotency_dates CHECK (
        expires_at > created_at AND
        (completed_at IS NULL OR (completed_at >= created_at AND expires_at > completed_at))
    )
);

CREATE INDEX sale_idempotency_expiration_idx ON sales.sale_idempotency_keys (expires_at);
