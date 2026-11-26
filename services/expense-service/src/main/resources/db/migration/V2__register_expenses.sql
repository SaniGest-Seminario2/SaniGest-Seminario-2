CREATE TABLE expense.documents (
    company_id UUID NOT NULL,
    id UUID NOT NULL,
    actor_id UUID NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    supplier_id UUID NOT NULL,
    document_date DATE NOT NULL,
    snapshot JSONB NOT NULL,
    inventory_synced BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (company_id, id),
    UNIQUE (company_id, actor_id, request_key)
);

CREATE TABLE expense.payables (
    company_id UUID NOT NULL,
    expense_id UUID NOT NULL,
    supplier_id UUID NOT NULL,
    due_date DATE NOT NULL,
    original_amount NUMERIC(32, 2) NOT NULL CHECK (original_amount > 0),
    paid_amount NUMERIC(32, 2) NOT NULL DEFAULT 0 CHECK (
        paid_amount >= 0 AND paid_amount <= original_amount
    ),
    PRIMARY KEY (company_id, expense_id),
    FOREIGN KEY (company_id, expense_id) REFERENCES expense.documents (company_id, id)
);

CREATE INDEX expense_payables_due_idx ON expense.payables (company_id, due_date, supplier_id);
