-- Add hash chain columns for tamper-evident audit logging
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS previous_hash VARCHAR(64);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS entry_hash VARCHAR(64);

-- Index on entry_hash for efficient chain lookups
CREATE INDEX IF NOT EXISTS idx_audit_log_entry_hash ON audit_log(entry_hash);

-- Set genesis hash for existing entries (if any)
UPDATE audit_log SET previous_hash = 'GENESIS', entry_hash = 'GENESIS' WHERE previous_hash IS NULL;
