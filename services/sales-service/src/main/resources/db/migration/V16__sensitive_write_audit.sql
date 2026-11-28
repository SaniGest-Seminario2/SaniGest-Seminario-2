CREATE TABLE sales.audit_log (
    id uuid PRIMARY KEY,
    company_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    operation varchar(40) NOT NULL,
    resource_id uuid NOT NULL,
    state varchar(32) NOT NULL,
    correlation_id varchar(32) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT statement_timestamp()
);

CREATE INDEX audit_log_company_timeline
    ON sales.audit_log (company_id, occurred_at DESC, id DESC);

CREATE FUNCTION sales.reject_audit_log_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Audit history is append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER audit_log_append_only
    BEFORE UPDATE OR DELETE OR TRUNCATE ON sales.audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION sales.reject_audit_log_mutation();
