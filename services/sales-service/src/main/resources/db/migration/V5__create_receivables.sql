-- Historical sales have no recorded due date and are not backfilled.
ALTER TABLE sales.sales
    ADD CONSTRAINT sales_company_id_sale_date_key UNIQUE (company_id, id, sale_date);

CREATE TABLE sales.receivables (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    sale_id UUID UNIQUE,
    sale_date DATE,
    client_id UUID NOT NULL,
    client_name VARCHAR(250) NOT NULL CHECK (client_name ~ '[^[:space:]]'),
    total_amount NUMERIC NOT NULL,
    balance NUMERIC NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(14) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    origin VARCHAR(15) NOT NULL,
    CONSTRAINT receivables_sale_fk FOREIGN KEY (company_id, sale_id, sale_date)
        REFERENCES sales.sales (company_id, id, sale_date),
    CONSTRAINT receivables_origin CHECK (
        (origin = 'SALE' AND sale_id IS NOT NULL AND sale_date IS NOT NULL)
        OR (origin = 'OPENING_BALANCE' AND sale_id IS NULL AND sale_date IS NULL)
    ),
    CONSTRAINT receivables_dates CHECK (
        due_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'
        AND (sale_date IS NULL OR due_date >= sale_date)
    ),
    CONSTRAINT receivables_amounts CHECK (
        total_amount > 0 AND total_amount < 100000000000000000
        AND total_amount = trunc(total_amount, 2)
        AND balance BETWEEN 0 AND total_amount AND balance = trunc(balance, 2)
    ),
    CONSTRAINT receivables_status_balance CHECK (
        (status IN ('OPEN', 'CANCELLED') AND balance = total_amount)
        OR (status = 'PARTIALLY_PAID' AND balance > 0 AND balance < total_amount)
        OR (status = 'PAID' AND balance = 0)
    )
);
