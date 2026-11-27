CREATE VIEW expense.document_balances AS
SELECT amounts.*,
       total - paid_amount AS outstanding_amount,
       CASE
           WHEN total = paid_amount THEN 'PAID'
           WHEN paid_amount = 0 THEN 'PENDING'
           ELSE 'PARTIALLY_PAID'
       END AS status
FROM (
    SELECT d.company_id, d.id, d.supplier_id, d.document_date, d.snapshot,
           d.snapshot ->> 'supplierName' AS supplier_name,
           d.snapshot ->> 'supplierInvoiceNumber' AS supplier_invoice_number,
           d.snapshot ->> 'operationType' AS operation_type,
           (d.snapshot ->> 'total')::numeric AS total,
           COALESCE(p.paid_amount, 0.00) AS paid_amount
    FROM expense.documents d
    LEFT JOIN expense.payables p ON p.company_id = d.company_id AND p.expense_id = d.id
) amounts;

CREATE INDEX expense_documents_date_idx ON expense.documents (company_id, document_date DESC, id);
