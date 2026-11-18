ALTER TABLE users
    ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'REGULAR';

ALTER TABLE users
    ADD CONSTRAINT ck_users_account_type CHECK (account_type IN ('REGULAR', 'ADVISOR'));
