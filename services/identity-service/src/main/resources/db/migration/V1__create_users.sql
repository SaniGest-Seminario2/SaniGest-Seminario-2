CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_normalized CHECK (email = LOWER(TRIM(email))),
    CONSTRAINT ck_users_email_not_blank CHECK (TRIM(email) <> ''),
    CONSTRAINT ck_users_password_hash_not_blank CHECK (TRIM(password_hash) <> ''),
    CONSTRAINT ck_users_name_not_blank CHECK (TRIM(name) <> ''),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_users_timestamps CHECK (updated_at >= created_at)
);
