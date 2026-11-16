CREATE TABLE companies (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    rnc VARCHAR(11),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_companies_name_not_blank CHECK (TRIM(name) <> ''),
    CONSTRAINT ck_companies_rnc_format CHECK (
        rnc IS NULL
        OR (CHAR_LENGTH(rnc) IN (9, 11) AND REGEXP_LIKE(rnc, '^[0-9]+$'))
    ),
    CONSTRAINT uq_companies_rnc UNIQUE (rnc)
);
