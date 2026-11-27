CREATE SCHEMA finance;

CREATE TABLE finance.cash_shifts (
    company_id uuid NOT NULL,
    id uuid NOT NULL,
    operator_id uuid NOT NULL,
    opened_at timestamptz NOT NULL,
    closed_at timestamptz CHECK (closed_at >= opened_at),
    PRIMARY KEY (company_id, id)
);

CREATE UNIQUE INDEX cash_shifts_one_open_per_operator
    ON finance.cash_shifts (company_id, operator_id) WHERE closed_at IS NULL;

CREATE TABLE finance.cash_accounts (
    company_id uuid NOT NULL,
    id uuid NOT NULL,
    PRIMARY KEY (company_id, id)
);

CREATE TABLE finance.shift_payment_accounts (
    company_id uuid NOT NULL,
    shift_id uuid NOT NULL,
    method text NOT NULL CHECK (method IN ('CASH', 'CREDIT_CARD', 'DEBIT_CARD', 'TRANSFER', 'CHECK')),
    account_id uuid NOT NULL,
    PRIMARY KEY (company_id, shift_id, method),
    FOREIGN KEY (company_id, shift_id) REFERENCES finance.cash_shifts (company_id, id),
    FOREIGN KEY (company_id, account_id) REFERENCES finance.cash_accounts (company_id, id)
);

CREATE TABLE finance.cash_movements (
    event_id uuid PRIMARY KEY,
    company_id uuid NOT NULL,
    event_type text NOT NULL,
    source_id uuid NOT NULL,
    payment_id uuid NOT NULL,
    shift_id uuid,
    account_id uuid NOT NULL,
    method text NOT NULL,
    amount numeric(19, 2) NOT NULL CHECK (amount > 0),
    currency char(3) NOT NULL,
    occurred_at timestamptz NOT NULL,
    FOREIGN KEY (company_id, shift_id) REFERENCES finance.cash_shifts (company_id, id),
    FOREIGN KEY (company_id, account_id) REFERENCES finance.cash_accounts (company_id, id)
);
