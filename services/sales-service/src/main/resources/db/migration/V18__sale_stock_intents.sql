CREATE TABLE sales.sale_stock_intents (
    company_id uuid NOT NULL,
    sale_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    revert_after timestamptz,
    PRIMARY KEY (company_id, sale_id)
);
CREATE INDEX sale_stock_intents_due ON sales.sale_stock_intents (revert_after)
    WHERE revert_after IS NOT NULL;
