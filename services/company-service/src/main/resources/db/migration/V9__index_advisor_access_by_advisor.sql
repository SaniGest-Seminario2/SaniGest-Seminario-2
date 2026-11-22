CREATE INDEX idx_company_advisor_access_advisor_company
    ON company_advisor_access (advisor_id, company_id);
