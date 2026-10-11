-- Rename the table to a plural noun and enforce normalized e-mail addresses at the DB level.
ALTER TABLE remitter_model RENAME TO remitters;
ALTER TABLE remitters RENAME CONSTRAINT uk_remitter_model_email_addr TO uk_remitters_email_addr;

-- Normalize existing rows before adding the check (same rule as EmailAddressNormalizer)
UPDATE remitters SET email_addr = LOWER(TRIM(email_addr));
ALTER TABLE remitters ADD CONSTRAINT ck_remitters_email_addr_normalized CHECK (email_addr = LOWER(TRIM(email_addr)));
