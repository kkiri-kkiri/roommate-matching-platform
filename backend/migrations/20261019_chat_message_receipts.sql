-- MANUAL ONLY. Stop/drain all old and new chat writers/connections first.
-- Read 20261019_chat_message_receipts.md, verify table case and back up before execution.
-- MySQL DDL implicitly commits: ADD COLUMN and receipt conversion are separate stages.
-- Run ADD COLUMN once; do not rerun it blindly after partial failure.
ALTER TABLE CHAT_MESSAGE ADD COLUMN is_read BOOLEAN NOT NULL DEFAULT FALSE;

-- Preserve the OLD displayed receipt for every existing message; this cannot establish
-- whether historical timestamp-based receipts actually corresponded to messages viewed.
-- Never rerun this conversion after the new application has started writing exact receipts.
-- Abort on errors; do not continue to COMMIT after a failed UPDATE.
START TRANSACTION;
UPDATE CHAT_MESSAGE m
JOIN CHAT_ROOM_MEMBER recipient
  ON recipient.chat_room_id = m.chat_room_id AND recipient.user_id <> m.sender_id
SET m.is_read = (recipient.last_read_at IS NOT NULL AND m.sent_at <= recipient.last_read_at);
COMMIT;
