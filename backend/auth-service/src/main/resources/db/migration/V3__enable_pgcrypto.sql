-- pgcrypto extension is already enabled in V1__create_users_table.sql
-- This migration documents the encryption-at-rest strategy for sensitive data.
--
-- Encryption Strategy:
-- 1. Refresh tokens are hashed with SHA-256 before storage (see AuthService.hashToken)
--    - SHA-256 is appropriate for high-entropy random tokens (not passwords)
--    - Enables direct DB lookup by hash without decryption overhead
--
-- 2. Passwords are hashed with BCrypt (cost factor 12) via Spring Security PasswordEncoder
--    - BCrypt is a slow hash designed for password storage
--    - Resistant to brute-force and rainbow table attacks
--
-- 3. Database-level encryption:
--    - PostgreSQL Transparent Data Encryption (TDE) should be enabled at the cluster level
--    - EBS/disk encryption provides encryption at rest for all stored data
--    - pgcrypto gen_random_uuid() is used for UUID generation
--
-- Ensure pgcrypto is available (idempotent)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Add index on deleted_at for efficient GDPR deletion queries
CREATE INDEX IF NOT EXISTS idx_users_deleted_at ON users(deleted_at) WHERE deleted_at IS NOT NULL;
