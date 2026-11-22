CREATE FUNCTION forbid_company_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Company audit log is append-only' USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER company_audit_log_append_only
BEFORE UPDATE OR DELETE OR TRUNCATE ON company_audit_log
FOR EACH STATEMENT EXECUTE FUNCTION forbid_company_audit_mutation();
