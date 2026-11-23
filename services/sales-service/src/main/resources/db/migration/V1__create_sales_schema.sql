CREATE SCHEMA sales AUTHORIZATION sanigest_sales;

CREATE TABLE sales.sales (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    client_id UUID NOT NULL,
    client_name VARCHAR(250) NOT NULL,
    sale_date DATE NOT NULL,
    mode VARCHAR(6) NOT NULL,
    status VARCHAR(10) NOT NULL,
    total NUMERIC NOT NULL,
    CONSTRAINT sales_client_name_present CHECK (client_name ~ '[^[:space:]]'),
    CONSTRAINT sales_date_range CHECK (sale_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    CONSTRAINT sales_mode_allowed CHECK (mode IN ('CASH', 'CREDIT')),
    CONSTRAINT sales_status_allowed CHECK (status IN ('REGISTERED', 'CANCELLED')),
    CONSTRAINT sales_total_amount CHECK (
        total >= 0 AND total < 100000000000000000 AND total = trunc(total, 2)
    )
);

-- Unconstrained NUMERIC avoids PostgreSQL rounding before CHECK runs. The checks
-- enforce the monetary envelope of NUMERIC(19,2) and quantity of NUMERIC(15,3).
CREATE TABLE sales.sale_lines (
    id UUID PRIMARY KEY,
    sale_id UUID NOT NULL,
    catalog_item_id UUID NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    item_description VARCHAR(1000),
    unit_price NUMERIC NOT NULL,
    quantity NUMERIC NOT NULL,
    subtotal NUMERIC NOT NULL,
    CONSTRAINT sale_lines_sale_fk FOREIGN KEY (sale_id) REFERENCES sales.sales (id),
    CONSTRAINT sale_lines_item_name_present CHECK (item_name ~ '[^[:space:]]'),
    CONSTRAINT sale_lines_unit_price_amount CHECK (
        unit_price >= 0 AND unit_price < 100000000000000000 AND unit_price = trunc(unit_price, 2)
    ),
    CONSTRAINT sale_lines_quantity_range CHECK (
        quantity > 0 AND quantity < 1000000000000 AND quantity = trunc(quantity, 3)
    ),
    CONSTRAINT sale_lines_subtotal_amount CHECK (
        subtotal >= 0 AND subtotal < 100000000000000000 AND subtotal = trunc(subtotal, 2)
    ),
    CONSTRAINT sale_lines_subtotal_exact CHECK (subtotal = unit_price * quantity)
);

CREATE INDEX sales_company_date_id_idx ON sales.sales (company_id, sale_date DESC, id DESC);
CREATE INDEX sale_lines_sale_id_idx ON sales.sale_lines (sale_id);
