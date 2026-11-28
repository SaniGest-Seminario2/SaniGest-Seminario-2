CREATE FUNCTION finance.reject_cash_movements_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Audit history is append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER cash_movements_append_only
    BEFORE UPDATE OR DELETE OR TRUNCATE ON finance.cash_movements
    FOR EACH STATEMENT EXECUTE FUNCTION finance.reject_cash_movements_mutation();
