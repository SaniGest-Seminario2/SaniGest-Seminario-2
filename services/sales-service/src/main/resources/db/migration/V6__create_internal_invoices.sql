CREATE TABLE sales.invoice_number_sequences (
    company_id UUID NOT NULL,
    issuance_year INTEGER NOT NULL CHECK (issuance_year BETWEEN 1 AND 9999),
    last_number BIGINT NOT NULL CHECK (last_number > 0),
    PRIMARY KEY (company_id, issuance_year)
);

CREATE TABLE sales.internal_invoices (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    sale_id UUID NOT NULL,
    sale_date DATE NOT NULL,
    number VARCHAR(28) NOT NULL,
    document_type VARCHAR(8) NOT NULL CHECK (document_type = 'INTERNAL'),
    fiscal_classification VARCHAR(10) NOT NULL CHECK (fiscal_classification = 'NOT_FISCAL'),
    status VARCHAR(6) NOT NULL CHECK (status = 'ISSUED'),
    issued_at TIMESTAMPTZ NOT NULL,
    issued_by UUID NOT NULL,
    company_name VARCHAR(250) NOT NULL CHECK (company_name ~ '[^[:space:]]'),
    client_id UUID NOT NULL,
    client_name VARCHAR(250) NOT NULL CHECK (client_name ~ '[^[:space:]]'),
    total NUMERIC NOT NULL CHECK (
        total >= 0 AND total < 100000000000000000 AND total = trunc(total, 2)
    ),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'DOP'),
    idempotency_key VARCHAR(128) NOT NULL CHECK (idempotency_key ~ '^[A-Za-z0-9._~-]{1,128}$'),
    response_body TEXT NOT NULL CHECK (response_body <> ''),
    CONSTRAINT invoice_sale_fk FOREIGN KEY (company_id, sale_id, sale_date)
        REFERENCES sales.sales (company_id, id, sale_date),
    CONSTRAINT invoice_company_number_key UNIQUE (company_id, number),
    CONSTRAINT invoice_request_key UNIQUE (company_id, issued_by, idempotency_key),
    CONSTRAINT invoice_number_format CHECK (
        number ~ '^FAC-[0-9]{4}-[0-9]{3,19}$'
        AND substring(number FROM 10) ~ '[1-9]'
        AND substring(number FROM 5 FOR 4) = to_char(issued_at AT TIME ZONE 'America/Santo_Domingo', 'YYYY')
    )
);

CREATE UNIQUE INDEX invoice_one_issued_per_sale
    ON sales.internal_invoices (company_id, sale_id) WHERE status = 'ISSUED';

CREATE TABLE sales.internal_invoice_lines (
    invoice_id UUID NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    source_line_id UUID NOT NULL,
    catalog_item_id UUID NOT NULL,
    item_name VARCHAR(200) NOT NULL CHECK (item_name ~ '[^[:space:]]'),
    item_description VARCHAR(1000),
    unit_price NUMERIC NOT NULL CHECK (
        unit_price >= 0 AND unit_price < 100000000000000000 AND unit_price = trunc(unit_price, 2)
    ),
    quantity NUMERIC NOT NULL CHECK (
        quantity > 0 AND quantity < 1000000000000 AND quantity = trunc(quantity, 3)
    ),
    subtotal NUMERIC NOT NULL CHECK (
        subtotal >= 0 AND subtotal < 100000000000000000 AND subtotal = trunc(subtotal, 2)
        AND subtotal = unit_price * quantity
    ),
    PRIMARY KEY (invoice_id, position),
    CONSTRAINT invoice_line_source_key UNIQUE (invoice_id, source_line_id),
    CONSTRAINT invoice_lines_invoice_fk FOREIGN KEY (invoice_id) REFERENCES sales.internal_invoices (id)
);
