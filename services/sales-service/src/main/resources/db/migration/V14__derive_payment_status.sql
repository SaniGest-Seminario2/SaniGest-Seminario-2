CREATE FUNCTION sales.payment_status(total numeric, balance numeric)
RETURNS text
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$
    SELECT CASE
        WHEN balance = 0 THEN 'PAID'
        WHEN total > balance THEN 'PARTIALLY_PAID'
        ELSE 'OPEN'
    END
$$;

ALTER TABLE sales.receivables DROP CONSTRAINT receivables_status_balance,
    ADD CONSTRAINT receivables_status_balance CHECK (
        CASE WHEN status = 'CANCELLED' THEN balance = total_amount
             ELSE status = sales.payment_status(total_amount, balance)
        END
    );
