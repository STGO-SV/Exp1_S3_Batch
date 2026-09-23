CREATE TABLE IF NOT EXISTS processed_anomaly_event (
    event_id UUID PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    event_version INTEGER NOT NULL,
    correlation_id VARCHAR(120),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    transaction_date DATE NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    anomaly_reason VARCHAR(80) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    topic VARCHAR(249) NOT NULL,
    partition_id INTEGER NOT NULL,
    offset_value BIGINT NOT NULL,
    consumer_instance VARCHAR(120) NOT NULL,
    CONSTRAINT uk_processed_anomaly_transaction UNIQUE (transaction_id),
    CONSTRAINT chk_processed_anomaly_version CHECK (event_version > 0)
);
