
CREATE TABLE IF NOT EXISTS eft_payment_operation (
 operation_id UUID PRIMARY KEY, actor VARCHAR(120) NOT NULL, idempotency_key VARCHAR(120) NOT NULL,
 request_hash VARCHAR(64) NOT NULL, request TEXT NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','COMPLETED','FAILED')),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, receipt TEXT,
 failure_code VARCHAR(120), failure_status INTEGER, UNIQUE(actor,idempotency_key)
);
CREATE TABLE IF NOT EXISTS eft_payment_event_audit (
 event_id UUID PRIMARY KEY, operation_id UUID NOT NULL, payload TEXT NOT NULL,
 received_at TIMESTAMP WITH TIME ZONE NOT NULL
);
