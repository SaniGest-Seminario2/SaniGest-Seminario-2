ALTER TABLE finance.cash_movements
    ADD COLUMN origin text,
    ADD COLUMN direction text CHECK (direction IN ('INFLOW', 'OUTFLOW')),
    ADD COLUMN document_id uuid,
    ADD COLUMN actor_id uuid,
    ADD COLUMN trace_id text,
    ADD CONSTRAINT cash_movements_source_key UNIQUE (company_id, origin, source_id);
