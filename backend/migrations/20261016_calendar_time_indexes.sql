-- Manual MySQL 8.0 migration. Never run through application initialization.
-- Confirm the target schema, SHOW CREATE TABLE / SHOW INDEX, row counts, existing
-- equivalent indexes, backups and DDL metadata-lock budget before executing.
-- The application keeps ddl-auto=validate in production; validate does not check indexes.
-- Non-UNIQUE: existing duplicates and utf8mb4_0900_ai_ci equality remain unchanged.
-- Full crawl key is ~1430 bytes at utf8mb4, below InnoDB's 3072-byte key limit.
-- Existing owner/category FK support indexes must be retained.
ALTER TABLE calendar_event
    ADD INDEX idx_calendar_crawl_identity (source_type, source_name, title, start_date, end_date),
    ADD INDEX idx_calendar_period (end_date, start_date, owner_user_id, source_type),
    ALGORITHM=INPLACE, LOCK=NONE;

-- Re-run EXPLAIN ANALYZE with representative public/private date ranges and compare
-- INSERT/UPDATE costs after application. Broad ranges still require proportional work.
-- Rollback, if independently reviewed:
-- ALTER TABLE calendar_event DROP INDEX idx_calendar_crawl_identity,
--     DROP INDEX idx_calendar_period, ALGORITHM=INPLACE, LOCK=NONE;
--
-- No DATETIME type or data conversion is included. New Calendar direct Java-time
-- mapping stores Seoul wall time. Older java.sql.Timestamp mapping may have stored
-- UTC-looking raw values under Seoul JVM / UTC JDBC. Mixed provenance prevents a
-- safe blanket +9h UPDATE. See DEPLOYMENT.md: identify proven row/run ID ranges from
-- historical configuration/logs, back up, review conversion separately, verify
-- started_at/completed_at/collected_at/created_at and midnight boundaries. Never
-- shift USER schedule DATE/TIME, which already represents the local calendar.
