-- MySQL 8.4. E-mail addresses use utf8mb4_0900_bin (case-sensitive, NO PAD) so that uniqueness and the
-- normalization check in V2 compare the exact stored value (the default collation ignores case).
CREATE TABLE remitter_model (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name  VARCHAR(255) NOT NULL,
    family_name VARCHAR(255) NOT NULL,
    email_addr  VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    CONSTRAINT uk_remitter_model_email_addr UNIQUE (email_addr)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
