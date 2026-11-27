ALTER TABLE expense.payable_payments ADD COLUMN request_fingerprint text;

COMMENT ON COLUMN expense.payable_payments.request_fingerprint IS
    'Versioned fingerprint. NULL denotes a legacy request matched against its stored JSON body.';
