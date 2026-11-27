CREATE TABLE finance.financial_snapshots (
    company_id uuid PRIMARY KEY,
    sales_as_of timestamptz NOT NULL,
    expense_as_of timestamptz NOT NULL,
    sales jsonb NOT NULL,
    expense jsonb NOT NULL
);

ALTER TABLE finance.cash_movements ADD COLUMN original_event_id uuid;

CREATE UNIQUE INDEX cash_movements_reversal_key
    ON finance.cash_movements (company_id, original_event_id)
    WHERE original_event_id IS NOT NULL;

CREATE INDEX cash_movements_company_activity_idx
    ON finance.cash_movements (company_id, occurred_at DESC, event_id);
