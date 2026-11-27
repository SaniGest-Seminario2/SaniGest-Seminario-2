ALTER TABLE expense.payables ADD COLUMN cancelled_at TIMESTAMPTZ;

CREATE TABLE expense.payable_payments (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    payable_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    request_body JSONB NOT NULL,
    response_body JSONB,
    UNIQUE (company_id, actor_id, request_key),
    FOREIGN KEY (company_id, payable_id) REFERENCES expense.payables (company_id, expense_id)
);
