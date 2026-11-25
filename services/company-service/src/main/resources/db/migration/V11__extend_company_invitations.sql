ALTER TABLE company_invitations ADD COLUMN recipient_name VARCHAR(160);
CREATE INDEX ix_company_memberships_page ON company_memberships (company_id, id);
