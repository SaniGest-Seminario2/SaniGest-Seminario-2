-- Invoices created together with a sale are replayed by the sale idempotency transaction.
ALTER TABLE sales.internal_invoices ALTER COLUMN idempotency_key DROP NOT NULL;
