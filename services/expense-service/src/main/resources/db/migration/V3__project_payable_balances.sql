CREATE VIEW expense.payable_balances AS
SELECT p.company_id,
       p.expense_id,
       p.supplier_id,
       d.snapshot ->> 'supplierName' AS supplier_name,
       d.snapshot ->> 'operationType' AS operation_type,
       d.document_date,
       p.due_date,
       p.due_date - d.document_date AS credit_days,
       p.original_amount,
       p.paid_amount,
       p.original_amount - p.paid_amount AS outstanding_amount,
       CASE
           WHEN p.paid_amount = 0 THEN 'OPEN'
           WHEN p.paid_amount < p.original_amount THEN 'PARTIALLY_PAID'
           ELSE 'PAID'
       END AS status
FROM expense.payables p
JOIN expense.documents d ON d.company_id = p.company_id AND d.id = p.expense_id;

CREATE VIEW expense.supplier_payable_balances AS
SELECT company_id, supplier_id, SUM(outstanding_amount) AS outstanding_amount
FROM expense.payable_balances
GROUP BY company_id, supplier_id;

CREATE INDEX expense_payables_supplier_idx ON expense.payables (company_id, supplier_id);
