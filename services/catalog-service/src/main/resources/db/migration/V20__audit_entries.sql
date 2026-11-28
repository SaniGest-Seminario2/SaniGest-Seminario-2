CREATE TABLE catalog.audit_log (
    id uuid PRIMARY KEY,
    company_id uuid NOT NULL,
    actor_id uuid,
    operation varchar(40) NOT NULL,
    resource_id uuid NOT NULL,
    resource_type varchar(32) NOT NULL,
    previous_state varchar(32) NOT NULL,
    state varchar(32) NOT NULL,
    correlation_id varchar(32) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT statement_timestamp()
);

CREATE INDEX audit_log_company_timeline
    ON catalog.audit_log (company_id, occurred_at DESC, id DESC);

CREATE FUNCTION catalog.reject_audit_log_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Audit history is append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER audit_log_append_only
    BEFORE UPDATE OR DELETE OR TRUNCATE ON catalog.audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.reject_audit_log_mutation();

CREATE VIEW catalog.audit_entries AS
SELECT id, company_id, actor_id, operation, resource_type, resource_id,
    occurred_at, 'SUCCESS'::text AS result, previous_state, state, correlation_id
FROM catalog.audit_log;
