-- Existing V1 sales may have no commercial code and retain their UUID line ordering.
ALTER TABLE sales.sales
    ADD COLUMN code VARCHAR(26),
    ADD CONSTRAINT sales_company_code_key UNIQUE (company_id, code),
    ADD CONSTRAINT sales_code_format CHECK (code ~ '^V-[0-9]{4}-[0-9]{3,19}$');

ALTER TABLE sales.sale_lines
    ADD COLUMN position INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT sale_lines_position_nonnegative CHECK (position >= 0);

CREATE TABLE sales.sale_number_sequences (
    company_id UUID NOT NULL,
    sale_year INTEGER NOT NULL CHECK (sale_year BETWEEN 1 AND 9999),
    last_number BIGINT NOT NULL CHECK (last_number > 0),
    PRIMARY KEY (company_id, sale_year)
);
