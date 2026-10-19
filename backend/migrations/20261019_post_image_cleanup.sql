-- MANUAL ONLY. Review backups, schema names, collation and online DDL before applying.
-- No automatic migration or historical-file sweep is performed by the application.
CREATE TABLE POST_IMAGE_CLEANUP (
    cleanup_id BIGINT NOT NULL AUTO_INCREMENT,
    image_url VARCHAR(500) NOT NULL,
    attempts INT NOT NULL,
    next_attempt_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (cleanup_id),
    INDEX idx_image_cleanup_due (next_attempt_at, cleanup_id)
) ENGINE=InnoDB;

-- Nonunique: legacy shared references are valid and must be protected (including DELETED posts).
-- Confirm an equivalent index does not already exist; validate collation and EXPLAIN separately.
CREATE INDEX idx_post_image_url ON POST_IMAGE (image_url);
