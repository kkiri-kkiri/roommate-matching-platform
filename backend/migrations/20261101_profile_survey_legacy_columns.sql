-- MANUAL ONLY, MySQL 8.0. No startup/task executes this file.
-- First stop writers, verify a full backup and review 20261101_profile_survey_legacy_columns.md.
-- In the SAME session explicitly set @kkiri_legacy_survey_approval='BACKUP_VERIFIED_WRITERS_STOPPED'.
-- Do not use mysql --force. DDL implicitly commits; this is not one rollbackable transaction.
DELIMITER $$
CREATE PROCEDURE kkiri_archive_survey_20261101()
BEGIN
    DECLARE n BIGINT DEFAULT 0;
    IF COALESCE(@kkiri_legacy_survey_approval, '') <> 'BACKUP_VERIFIED_WRITERS_STOPPED'
       OR NOT (DATABASE() = 'kkirikkiri' OR DATABASE() REGEXP '^kkirikkiri_demo50_[a-zA-Z0-9_]+$') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Explicit approval and reviewed target required';
    END IF;
    SELECT COUNT(*) INTO n FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='profile_survey'
        AND column_name IN ('bug_handling','hair_dryer_use')
        AND column_type='varchar(30)' AND is_nullable='NO' AND column_default IS NULL;
    IF n <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy column definitions differ; stop and review';
    END IF;
    SELECT COUNT(*) INTO n FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='profile_survey'
        AND column_name IN ('bug_handling','hair_dryer_use');
    IF n <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy column has an index'; END IF;
    SELECT COUNT(*) INTO n FROM information_schema.key_column_usage
      WHERE (table_schema=DATABASE() AND table_name='profile_survey' AND column_name IN ('bug_handling','hair_dryer_use'))
         OR (referenced_table_schema=DATABASE() AND referenced_table_name='profile_survey' AND referenced_column_name IN ('bug_handling','hair_dryer_use'));
    IF n <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy column has a key dependency'; END IF;
    SELECT COUNT(*) INTO n FROM (
      SELECT view_definition AS definition FROM information_schema.views WHERE table_schema=DATABASE()
      UNION ALL SELECT action_statement FROM information_schema.triggers WHERE trigger_schema=DATABASE()
      UNION ALL SELECT routine_definition FROM information_schema.routines WHERE routine_schema=DATABASE() AND routine_name<>'kkiri_archive_survey_20261101'
      UNION ALL SELECT generation_expression FROM information_schema.columns WHERE table_schema=DATABASE()
      UNION ALL SELECT check_clause FROM information_schema.check_constraints WHERE constraint_schema=DATABASE()
    ) definitions WHERE definition LIKE '%bug_handling%' OR definition LIKE '%hair_dryer_use%';
    IF n <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy dependency remains'; END IF;
    -- No IF NOT EXISTS: an existing archive must never be adopted or overwritten.
    CREATE TABLE profile_survey_legacy_20261101 (
        profile_survey_id BIGINT NOT NULL PRIMARY KEY,
        user_id BIGINT NOT NULL,
        bug_handling VARCHAR(30) NOT NULL,
        hair_dryer_use VARCHAR(30) NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    START TRANSACTION;
    INSERT INTO profile_survey_legacy_20261101
      SELECT profile_survey_id,user_id,bug_handling,hair_dryer_use FROM profile_survey;
    SELECT COUNT(*) INTO n FROM profile_survey s LEFT JOIN profile_survey_legacy_20261101 a
      ON a.profile_survey_id=s.profile_survey_id
      WHERE a.profile_survey_id IS NULL OR a.user_id<>s.user_id
        OR NOT (BINARY a.bug_handling <=> BINARY s.bug_handling)
        OR NOT (BINARY a.hair_dryer_use <=> BINARY s.hair_dryer_use);
    IF n <> 0 THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Archive row verification failed; no columns dropped';
    END IF;
    COMMIT;
    ALTER TABLE profile_survey DROP COLUMN bug_handling, DROP COLUMN hair_dryer_use;
END$$
DELIMITER ;
CALL kkiri_archive_survey_20261101();
DROP PROCEDURE kkiri_archive_survey_20261101;
