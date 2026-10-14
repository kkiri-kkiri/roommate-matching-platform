-- Manual MySQL migration only. Confirm the schema/table naming and back up first.
-- Not loaded by Spring SQL initialization. DDL implicitly commits in MySQL.
-- No existing users, credentials or tokens are changed.
CREATE TABLE auth_limit_bucket (
    bucket_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    window_started_at BIGINT NOT NULL,
    generation BIGINT NOT NULL,
    request_count INT NOT NULL,
    failure_count INT NOT NULL,
    in_flight INT NOT NULL,
    blocked_until BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (bucket_key),
    INDEX idx_auth_limit_updated_at (updated_at)
) ENGINE=InnoDB;
