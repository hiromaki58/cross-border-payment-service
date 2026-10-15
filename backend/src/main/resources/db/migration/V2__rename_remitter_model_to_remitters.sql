-- Rename the table to a plural noun and enforce normalized e-mail addresses at the DB level.
ALTER TABLE remitter_model RENAME TO remitters;
-- MySQL cannot rename a constraint: drop and re-create the unique key under the new name
ALTER TABLE remitters DROP CONSTRAINT uk_remitter_model_email_addr;
ALTER TABLE remitters ADD CONSTRAINT uk_remitters_email_addr UNIQUE (email_addr);

-- Normalize existing rows before adding the check (same rule as EmailAddressNormalizer)
UPDATE remitters SET email_addr = LOWER(TRIM(email_addr));
ALTER TABLE remitters ADD CONSTRAINT ck_remitters_email_addr_normalized CHECK (email_addr = LOWER(TRIM(email_addr)));
