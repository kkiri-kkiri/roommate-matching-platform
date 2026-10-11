-- MySQL: stop all old application instances before running this script.
-- Back up CHAT_ROOM first. See README.md for preflight and rollback requirements.
-- No DDL, message updates, or room/member deletions. Safe to rerun.
START TRANSACTION;

UPDATE CHAT_ROOM r
JOIN COMMUNITY_POST p ON p.post_id = r.source_post_id
SET r.room_key = CONCAT(r.room_key, ':POST:', p.post_id)
WHERE r.category = 'COMMUNITY'
  AND r.room_key REGEXP '^COMMUNITY:[0-9]+:[0-9]+$'
  AND p.anonymous = TRUE;

UPDATE CHAT_ROOM r
JOIN COMMUNITY_COMMENT c ON c.comment_id = r.source_comment_id
SET r.room_key = CONCAT(r.room_key, ':COMMENT:', c.comment_id)
WHERE r.category = 'COMMUNITY'
  AND r.room_key REGEXP '^COMMUNITY:[0-9]+:[0-9]+$'
  AND c.anonymous = TRUE;

COMMIT;
