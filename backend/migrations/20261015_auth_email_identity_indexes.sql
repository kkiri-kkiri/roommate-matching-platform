-- Manual MySQL 8 migration. Confirm DATABASE(), source DDL, backup and privileges first.
-- No existing email collation or data is changed. DDL implicitly commits.
-- The identity columns inherit comparison rules separately from their source columns.
SET @auth_account_charset = (SELECT character_set_name FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'school_email' AND column_type = 'varchar(100)');
SET @auth_account_collation = (SELECT collation_name FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'school_email');
SET @auth_verification_charset = (SELECT character_set_name FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email_verification' AND column_name = 'email' AND column_type = 'varchar(100)');
SET @auth_verification_collation = (SELECT collation_name FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email_verification' AND column_name = 'email');
SET @auth_identity_ddl = IF(
    @auth_account_charset REGEXP '^[a-zA-Z0-9_]+$' AND @auth_account_collation REGEXP '^[a-zA-Z0-9_]+$'
    AND @auth_verification_charset REGEXP '^[a-zA-Z0-9_]+$' AND @auth_verification_collation REGEXP '^[a-zA-Z0-9_]+$'
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='auth_email_identity')=0
    AND (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='email_verification'
         AND index_name IN ('idx_email_verification_latest','idx_email_verification_token'))=0,
    CONCAT('CREATE TABLE auth_email_identity (identity_id BIGINT NOT NULL AUTO_INCREMENT, ',
        'account_email VARCHAR(100) CHARACTER SET ', @auth_account_charset, ' COLLATE ', @auth_account_collation, ' NULL, ',
        'verification_email VARCHAR(100) CHARACTER SET ', @auth_verification_charset, ' COLLATE ', @auth_verification_collation, ' NULL, ',
        'PRIMARY KEY (identity_id), UNIQUE KEY uk_auth_identity_account (account_email), ',
        'UNIQUE KEY uk_auth_identity_verification (verification_email)) ENGINE=InnoDB'),
    'AUTH_EMAIL_IDENTITY_MIGRATION_PRECONDITION_FAILED');
PREPARE auth_identity_statement FROM @auth_identity_ddl;
EXECUTE auth_identity_statement;
DEALLOCATE PREPARE auth_identity_statement;

CREATE INDEX idx_email_verification_latest
    ON email_verification (email, purpose, created_at DESC, verification_id DESC);
CREATE INDEX idx_email_verification_token
    ON email_verification (email, purpose, verification_token_hash);
