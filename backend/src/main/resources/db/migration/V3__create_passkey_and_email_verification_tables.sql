-- Passkey (WebAuthn) data used by Spring Security's JdbcPublicKeyCredentialUserEntityRepository
-- and JdbcUserCredentialRepository (table and column names are fixed by those classes).
--
-- MySQL 8.4 notes:
-- - Credential ids and user handles are Base64url strings, which are case-sensitive: they use
--   ascii_bin (the default collation would treat "AbC" and "abc" as the same key). ASCII also keeps
--   the 1000-character keys within InnoDB's 3072-byte index limit.
-- - Date-times are DATETIME(6) in UTC (the application and the connection use UTC).

-- WebAuthn user entity. "name" is the remitter's (normalized) e-mail address, so a passkey user
-- can only exist for a registered remitter; it follows e-mail changes and is removed with the remitter.
CREATE TABLE user_entities (
    id           VARCHAR(1000) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    name         VARCHAR(255)  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    display_name VARCHAR(255),
    CONSTRAINT uk_user_entities_name UNIQUE (name),
    CONSTRAINT fk_user_entities_remitters FOREIGN KEY (name)
        REFERENCES remitters (email_addr) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Registered passkeys. Only the public key is stored; the private key never leaves the authenticator.
CREATE TABLE user_credentials (
    credential_id                VARCHAR(1000) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    user_entity_user_id          VARCHAR(1000) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    public_key                   BLOB          NOT NULL,
    signature_count              BIGINT        NOT NULL,
    uv_initialized               BOOLEAN       NOT NULL,
    backup_eligible              BOOLEAN       NOT NULL,
    authenticator_transports     VARCHAR(1000),
    public_key_credential_type   VARCHAR(100),
    backup_state                 BOOLEAN       NOT NULL,
    attestation_object           BLOB,
    attestation_client_data_json BLOB,
    created                      DATETIME(6)   NOT NULL,
    last_used                    DATETIME(6)   NOT NULL,
    label                        VARCHAR(1000) NOT NULL,
    CONSTRAINT fk_user_credentials_user_entities FOREIGN KEY (user_entity_user_id)
        REFERENCES user_entities (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_user_credentials_user_entity_user_id ON user_credentials (user_entity_user_id);

-- One-time codes sent by e-mail to verify address ownership before a passkey is registered
-- (new registration or account recovery). Only a keyed hash of the code is stored.
-- code_hash is NULL when no code was sent (e.g. registration for an address that already exists).
CREATE TABLE email_verification_challenges (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    purpose       VARCHAR(20)  NOT NULL,
    email_addr    VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    first_name    VARCHAR(255),
    family_name   VARCHAR(255),
    code_hash     VARCHAR(64),
    attempt_count INTEGER      NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    expires_at    DATETIME(6)  NOT NULL,
    consumed_at   DATETIME(6),
    CONSTRAINT ck_email_verification_challenges_purpose CHECK (purpose IN ('REGISTRATION', 'RECOVERY')),
    CONSTRAINT ck_email_verification_challenges_email_addr_normalized CHECK (email_addr = LOWER(TRIM(email_addr)))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Used to limit how many codes can be requested for one address within a time window
CREATE INDEX ix_email_verification_challenges_email_addr_created_at
    ON email_verification_challenges (email_addr, created_at);
