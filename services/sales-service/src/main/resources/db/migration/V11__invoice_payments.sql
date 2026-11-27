ALTER TABLE sales.internal_invoices
    ADD CONSTRAINT internal_invoices_company_id_key UNIQUE (company_id, id);

ALTER TABLE sales.receivables
    ADD CONSTRAINT receivables_company_id_key UNIQUE (company_id, id);

CREATE TABLE sales.invoice_payments (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    operator_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_body JSONB NOT NULL,
    response_body TEXT,
    receivable_id UUID,
    shift_id UUID,
    registered_at TIMESTAMPTZ,
    amount NUMERIC(19, 2) CHECK (amount > 0),
    balance NUMERIC(19, 2) CHECK (balance >= 0),
    notes VARCHAR(500),
    UNIQUE (company_id, operator_id, idempotency_key),
    CONSTRAINT invoice_payments_invoice_fk FOREIGN KEY (company_id, invoice_id)
        REFERENCES sales.internal_invoices (company_id, id),
    CONSTRAINT invoice_payments_receivable_fk FOREIGN KEY (company_id, receivable_id)
        REFERENCES sales.receivables (company_id, id)
);

CREATE TABLE sales.invoice_payment_lines (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    method VARCHAR(16) NOT NULL CHECK (method IN ('CASH', 'CREDIT_CARD', 'DEBIT_CARD', 'TRANSFER', 'CHECK')),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    reference VARCHAR(250),
    account_id UUID NOT NULL,
    UNIQUE (payment_id, position),
    CONSTRAINT invoice_payment_lines_payment_fk FOREIGN KEY (payment_id)
        REFERENCES sales.invoice_payments (id)
);
