CREATE TABLE IF NOT EXISTS eft_account (
    account_id BIGINT PRIMARY KEY,
    account_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_eft_account_id CHECK (account_id > 0),
    CONSTRAINT chk_eft_account_type CHECK (account_type IN ('ahorro','prestamo')),
    CONSTRAINT chk_eft_account_status CHECK (status IN ('OPEN','CLOSED')),
    CONSTRAINT chk_eft_account_version CHECK (version >= 0)
);
CREATE TABLE IF NOT EXISTS eft_account_holder (
    account_id BIGINT NOT NULL REFERENCES eft_account(account_id),
    customer_id UUID NOT NULL,
    PRIMARY KEY (account_id, customer_id)
);
CREATE INDEX IF NOT EXISTS idx_eft_account_holder_customer ON eft_account_holder(customer_id, account_id);