CREATE TABLE finance.manual_cash_operations (
    company_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    fingerprint text NOT NULL,
    id uuid NOT NULL,
    details jsonb NOT NULL,
    document_type text,
    document_content bytea,
    PRIMARY KEY (company_id, actor_id, idempotency_key),
    UNIQUE (company_id, id),
    CHECK ((document_type IS NULL) = (document_content IS NULL))
);

ALTER TABLE finance.cash_movements
    ADD COLUMN manual_operation_id uuid,
    ADD FOREIGN KEY (company_id, manual_operation_id)
        REFERENCES finance.manual_cash_operations (company_id, id);

CREATE UNIQUE INDEX cash_movements_manual_legs
    ON finance.cash_movements (company_id, manual_operation_id, direction)
    WHERE manual_operation_id IS NOT NULL;
