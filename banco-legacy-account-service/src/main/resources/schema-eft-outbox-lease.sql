-- Additive PostgreSQL migration, atomic and repeatable even with concurrent startup.
BEGIN;
ALTER TABLE eft_financial_outbox ADD COLUMN IF NOT EXISTS claim_owner VARCHAR(240);
ALTER TABLE eft_financial_outbox ADD COLUMN IF NOT EXISTS claim_token UUID;
ALTER TABLE eft_financial_outbox ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE eft_financial_outbox ADD COLUMN IF NOT EXISTS lease_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE eft_financial_outbox ADD COLUMN IF NOT EXISTS retry_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE eft_financial_outbox DROP CONSTRAINT IF EXISTS eft_financial_outbox_status_check;
ALTER TABLE eft_financial_outbox DROP CONSTRAINT IF EXISTS chk_eft_financial_outbox_status;
ALTER TABLE eft_financial_outbox ADD CONSTRAINT chk_eft_financial_outbox_status
 CHECK(status IN ('PENDING','PROCESSING','PUBLISHED')) NOT VALID;
ALTER TABLE eft_financial_outbox VALIDATE CONSTRAINT chk_eft_financial_outbox_status;
CREATE INDEX IF NOT EXISTS idx_eft_financial_outbox_claim ON eft_financial_outbox(status,lease_until,retry_at,event_id);
COMMIT;
