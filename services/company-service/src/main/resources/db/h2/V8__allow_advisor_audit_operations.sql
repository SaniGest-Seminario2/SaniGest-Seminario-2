EXECUTE IMMEDIATE (
    SELECT 'ALTER TABLE company_audit_log DROP CONSTRAINT "' || tc.constraint_name || '"'
    FROM information_schema.table_constraints tc
    JOIN information_schema.check_constraints cc ON cc.constraint_name = tc.constraint_name
        AND cc.constraint_schema = tc.constraint_schema
    WHERE tc.table_schema = CURRENT_SCHEMA AND UPPER(tc.table_name) = 'COMPANY_AUDIT_LOG'
        AND cc.check_clause LIKE '%MEMBER_ACTIVATED%'
);
ALTER TABLE company_audit_log ADD CONSTRAINT company_audit_log_operation_check CHECK (
    operation IN ('ROLE_CHANGED', 'MEMBER_ACTIVATED', 'MEMBER_REVOKED', 'ADVISOR_GRANTED', 'ADVISOR_REVOKED')
);

ALTER TABLE company_audit_log ADD CONSTRAINT company_audit_advisor_metadata CHECK (
    (operation IN ('ADVISOR_GRANTED', 'ADVISOR_REVOKED') AND result = 'SUCCESS'
        AND target_user_id IS NOT NULL AND advisor_module IS NOT NULL AND advisor_scope IS NOT NULL
        AND previous_role IS NULL AND new_role IS NULL AND previous_status IS NULL AND new_status IS NULL)
    OR (operation = 'ADVISOR_GRANTED' AND result = 'REJECTED'
        AND target_user_id IS NULL AND advisor_module IS NULL AND advisor_scope IS NULL)
    OR (operation NOT IN ('ADVISOR_GRANTED', 'ADVISOR_REVOKED')
        AND advisor_module IS NULL AND advisor_scope IS NULL)
);
