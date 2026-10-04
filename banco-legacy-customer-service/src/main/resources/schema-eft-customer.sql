CREATE TABLE IF NOT EXISTS eft_customer (
    customer_id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_eft_customer_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 120),
    CONSTRAINT chk_eft_customer_version CHECK (version >= 0)
);