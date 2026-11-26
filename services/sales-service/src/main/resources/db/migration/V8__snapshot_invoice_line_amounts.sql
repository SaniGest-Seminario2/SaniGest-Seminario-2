ALTER TABLE sales.sale_lines
    ADD COLUMN discount NUMERIC,
    ADD COLUMN tax_rate NUMERIC,
    DROP CONSTRAINT sale_lines_unit_price_amount,
    DROP CONSTRAINT sale_lines_subtotal_exact;

ALTER TABLE sales.internal_invoice_lines
    ADD COLUMN discount NUMERIC,
    ADD COLUMN tax_rate NUMERIC,
    DROP CONSTRAINT internal_invoice_lines_unit_price_check,
    DROP CONSTRAINT internal_invoice_lines_check;

ALTER TABLE sales.sale_lines ADD CONSTRAINT sale_lines_unit_pricing_check CHECK (
    unit_price >= 0 AND (
        (discount IS NULL AND tax_rate IS NULL
         AND unit_price < 100000000000000000 AND unit_price = trunc(unit_price, 2)
         AND subtotal = unit_price * quantity)
        OR
        (discount IS NOT NULL AND tax_rate IS NOT NULL
         AND unit_price < 1000000000000000 AND unit_price = trunc(unit_price, 4)
         AND discount BETWEEN 0 AND unit_price * quantity AND discount = trunc(discount, 2)
         AND tax_rate >= 0 AND tax_rate < 1 AND tax_rate = trunc(tax_rate, 6)
         AND subtotal = round(unit_price * quantity - discount, 2))
    )
);

ALTER TABLE sales.internal_invoice_lines ADD CONSTRAINT invoice_lines_pricing_check CHECK (
    unit_price >= 0 AND subtotal >= 0 AND subtotal < 100000000000000000
    AND subtotal = trunc(subtotal, 2) AND (
        (discount IS NULL AND tax_rate IS NULL
         AND unit_price < 100000000000000000 AND unit_price = trunc(unit_price, 2)
         AND subtotal = unit_price * quantity)
        OR
        (discount IS NOT NULL AND tax_rate IS NOT NULL
         AND unit_price < 1000000000000000 AND unit_price = trunc(unit_price, 4)
         AND discount BETWEEN 0 AND unit_price * quantity AND discount = trunc(discount, 2)
         AND tax_rate >= 0 AND tax_rate < 1 AND tax_rate = trunc(tax_rate, 6)
         AND subtotal = round(unit_price * quantity - discount, 2))
    )
);
