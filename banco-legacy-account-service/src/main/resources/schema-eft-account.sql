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
CREATE TABLE IF NOT EXISTS eft_account_balance (
 account_id BIGINT PRIMARY KEY REFERENCES eft_account(account_id),
 balance DECIMAL(19,2) NOT NULL DEFAULT 0 CHECK(balance >= 0)
);
INSERT INTO eft_account_balance(account_id,balance)
 SELECT account_id,0 FROM eft_account a WHERE NOT EXISTS(SELECT 1 FROM eft_account_balance b WHERE b.account_id=a.account_id);
CREATE TABLE IF NOT EXISTS eft_account_posting (
 operation_id UUID PRIMARY KEY, actor VARCHAR(120) NOT NULL, idempotency_key VARCHAR(120) NOT NULL,
 request_hash VARCHAR(64) NOT NULL, receipt TEXT NOT NULL,
 UNIQUE(actor,idempotency_key)
);
CREATE TABLE IF NOT EXISTS eft_financial_outbox (
 event_id UUID PRIMARY KEY, operation_id UUID NOT NULL UNIQUE REFERENCES eft_account_posting(operation_id),
 payload TEXT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','PUBLISHED')),
 attempts INTEGER NOT NULL DEFAULT 0, published_at TIMESTAMP WITH TIME ZONE, last_error VARCHAR(120)
);
