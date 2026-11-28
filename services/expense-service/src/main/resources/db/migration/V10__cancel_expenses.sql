ALTER TABLE expense.documents
    ADD COLUMN cancelled_at timestamptz,
    ADD COLUMN cancellation_reason varchar(1000),
    ADD CONSTRAINT expense_cancellation_metadata CHECK (
        (cancelled_at IS NULL) = (cancellation_reason IS NULL)
    );

CREATE OR REPLACE VIEW expense.payable_balances AS
SELECT p.company_id, p.expense_id, p.supplier_id,
       d.snapshot ->> 'supplierName' AS supplier_name,
       d.snapshot ->> 'operationType' AS operation_type,
       d.document_date, p.due_date, p.due_date - d.document_date AS credit_days,
       p.original_amount, p.paid_amount, p.original_amount - p.paid_amount AS outstanding_amount,
       CASE
           WHEN p.paid_amount = 0 THEN 'OPEN'
           WHEN p.paid_amount < p.original_amount THEN 'PARTIALLY_PAID'
           ELSE 'PAID'
       END AS status
FROM expense.payables p
JOIN expense.documents d ON d.company_id = p.company_id AND d.id = p.expense_id
WHERE p.cancelled_at IS NULL;

CREATE OR REPLACE VIEW expense.document_balances AS
SELECT d.company_id, d.id, d.supplier_id, d.document_date, d.snapshot,
       d.snapshot ->> 'supplierName' AS supplier_name,
       d.snapshot ->> 'supplierInvoiceNumber' AS supplier_invoice_number,
       d.snapshot ->> 'operationType' AS operation_type,
       (d.snapshot ->> 'total')::numeric AS total,
       COALESCE(p.paid_amount, 0.00) AS paid_amount,
       CASE WHEN d.cancelled_at IS NOT NULL THEN 0.00
            ELSE (d.snapshot ->> 'total')::numeric - COALESCE(p.paid_amount, 0.00)
       END AS outstanding_amount,
       CASE
           WHEN d.cancelled_at IS NOT NULL THEN 'CANCELLED'
           WHEN (d.snapshot ->> 'total')::numeric = COALESCE(p.paid_amount, 0.00) THEN 'PAID'
           WHEN COALESCE(p.paid_amount, 0.00) = 0 THEN 'PENDING'
           ELSE 'PARTIALLY_PAID'
       END AS status,
       d.cancelled_at, d.cancellation_reason
FROM expense.documents d
LEFT JOIN expense.payables p ON p.company_id = d.company_id AND p.expense_id = d.id;
