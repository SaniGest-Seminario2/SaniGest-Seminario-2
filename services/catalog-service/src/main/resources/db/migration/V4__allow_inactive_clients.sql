ALTER TABLE catalog.clients
    DROP CONSTRAINT ck_clients_status,
    ADD CONSTRAINT ck_clients_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
